package com.apulse.middleware.api;

import com.apulse.middleware.config.ReaderConfig;
import com.apulse.middleware.db.AssetRepository;
import com.apulse.middleware.db.DatabaseManager;
import com.apulse.middleware.db.TagRepository;
import com.apulse.middleware.reader.ReaderConnection;
import com.apulse.middleware.reader.ReaderManager;
import com.apulse.middleware.util.AppLogger;

import com.apulse.readerfinderlib.FixedReaderFinder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.Executors;

public class ApiServer {
    private static final int PORT = 18080;

    private final HttpServer server;
    private final ReaderManager readerManager;
    private final String configFile;

    public ApiServer(ReaderManager readerManager, List<ReaderConfig> configs, String configFile) throws IOException {
        this.readerManager = readerManager;
        this.configFile = configFile;

        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        server.createContext("/api/readers", new ReadersHandler());
        server.createContext("/api/assets", new AssetsHandler());
        server.createContext("/api/export-permissions", new ExportPermissionsHandler());
        server.createContext("/api/export-alerts", new ExportAlertsHandler());
        server.createContext("/api/control", new ControlHandler());
        server.createContext("/api/tags/recent", new RecentTagsHandler());
        server.createContext("/api/mask", new MaskHandler());
        server.createContext("/api/reader-finder", new ReaderFinderHandler());
        server.createContext("/swagger", new SwaggerUiHandler());
        server.createContext("/api/openapi.json", new OpenApiHandler());
        server.createContext("/", new DashboardHandler());
    }

    public void start() {
        server.start();
        AppLogger.info("ApiServer", "Started on port " + PORT);
    }

    public void shutdown() {
        server.stop(1);
        AppLogger.info("ApiServer", "Shutdown complete");
    }

    // --- Utility methods ---

    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendOk(HttpExchange exchange, String dataJson) throws IOException {
        sendJson(exchange, 200, "{\"status\":\"ok\",\"data\":" + dataJson + "}");
    }

    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        sendJson(exchange, code, "{\"status\":\"error\",\"message\":\"" + escapeJson(message) + "\"}");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String toJsonString(String value) {
        if (value == null) return "null";
        return "\"" + escapeJson(value) + "\"";
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            return bos.toString("UTF-8");
        }
    }

    /** Simple JSON field extractor (no external library) */
    private static Map<String, String> parseJsonFields(String json) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) return fields;

        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        int i = 0;
        while (i < json.length()) {
            // find key
            int keyStart = json.indexOf('"', i);
            if (keyStart < 0) break;
            int keyEnd = json.indexOf('"', keyStart + 1);
            if (keyEnd < 0) break;
            String key = json.substring(keyStart + 1, keyEnd);

            // find colon
            int colon = json.indexOf(':', keyEnd + 1);
            if (colon < 0) break;

            // find value
            int valStart = colon + 1;
            while (valStart < json.length() && json.charAt(valStart) == ' ') valStart++;

            String value;
            if (valStart < json.length() && json.charAt(valStart) == '"') {
                // string value
                int valEnd = findClosingQuote(json, valStart + 1);
                value = json.substring(valStart + 1, valEnd);
                // unescape
                value = value.replace("\\\"", "\"").replace("\\\\", "\\");
                i = valEnd + 1;
            } else if (valStart < json.length() && json.substring(valStart).startsWith("null")) {
                value = null;
                i = valStart + 4;
            } else {
                // number or boolean
                int valEnd = valStart;
                while (valEnd < json.length() && json.charAt(valEnd) != ',' && json.charAt(valEnd) != '}') {
                    valEnd++;
                }
                value = json.substring(valStart, valEnd).trim();
                i = valEnd;
            }

            fields.put(key, value);

            // skip comma
            while (i < json.length() && (json.charAt(i) == ',' || json.charAt(i) == ' ')) i++;
        }
        return fields;
    }

    private static int findClosingQuote(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                return i;
            }
        }
        return s.length();
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                    String val = URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                    params.put(key, val);
                } catch (UnsupportedEncodingException e) {
                    // ignore
                }
            }
        }
        return params;
    }

    // --- Handlers ---

    /** GET /api/readers - list all readers; PUT /api/readers/{name} - update reader config */
    private class ReadersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();

                if ("OPTIONS".equals(method)) {
                    sendJson(exchange, 204, "");
                    return;
                }

                String path = exchange.getRequestURI().getPath(); // /api/readers or /api/readers/{name}
                String subPath = path.length() > "/api/readers".length()
                    ? path.substring("/api/readers/".length()) : null;

                if ("GET".equals(method) && subPath == null) {
                    handleGetReaders(exchange);
                } else if ("PUT".equals(method) && subPath != null) {
                    handlePutReader(exchange, URLDecoder.decode(subPath, "UTF-8"));
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }

        private void handleGetReaders(HttpExchange exchange) throws IOException {
            List<ReaderConnection> connections = readerManager.getConnections();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < connections.size(); i++) {
                if (i > 0) sb.append(",");
                ReaderConfig cfg = connections.get(i).getConfig();
                int[] powers = cfg.getAntennaPowers();
                sb.append("{")
                    .append("\"name\":").append(toJsonString(cfg.getName())).append(",")
                    .append("\"ip\":").append(toJsonString(cfg.getIp())).append(",")
                    .append("\"port\":").append(cfg.getPort()).append(",")
                    .append("\"buzzer\":").append(cfg.isBuzzerEnabled()).append(",")
                    .append("\"warningLight\":").append(cfg.isWarningLightEnabled()).append(",")
                    .append("\"antennaPowers\":[")
                        .append(powers[0]).append(",").append(powers[1]).append(",")
                        .append(powers[2]).append(",").append(powers[3])
                    .append("],")
                    .append("\"dwellTime\":").append(cfg.getDwellTime()).append(",")
                    .append("\"beepEnabled\":").append(cfg.isBeepEnabled()).append(",")
                    .append("\"status\":").append(toJsonString(connections.get(i).getStatus().name()))
                    .append("}");
            }
            sb.append("]");
            sendOk(exchange, sb.toString());
        }

        private void handlePutReader(HttpExchange exchange, String readerName) throws IOException {
            // Find the reader config by name
            List<ReaderConnection> connections = readerManager.getConnections();
            ReaderConfig targetConfig = null;
            for (ReaderConnection conn : connections) {
                if (conn.getConfig().getName().equals(readerName)) {
                    targetConfig = conn.getConfig();
                    break;
                }
            }
            if (targetConfig == null) {
                sendError(exchange, 404, "Reader not found: " + readerName);
                return;
            }

            String body = readRequestBody(exchange);
            Map<String, String> fields = parseJsonFields(body);

            if (fields.containsKey("ip")) targetConfig.setIp(fields.get("ip"));
            if (fields.containsKey("port")) targetConfig.setPort(Integer.parseInt(fields.get("port")));
            if (fields.containsKey("buzzer")) targetConfig.setBuzzerEnabled(Boolean.parseBoolean(fields.get("buzzer")));
            if (fields.containsKey("warningLight")) targetConfig.setWarningLightEnabled(Boolean.parseBoolean(fields.get("warningLight")));
            if (fields.containsKey("dwellTime")) targetConfig.setDwellTime(Integer.parseInt(fields.get("dwellTime")));
            if (fields.containsKey("beepEnabled")) targetConfig.setBeepEnabled(Boolean.parseBoolean(fields.get("beepEnabled")));
            if (fields.containsKey("antennaPower1") || fields.containsKey("antennaPower2")
                || fields.containsKey("antennaPower3") || fields.containsKey("antennaPower4")) {
                int[] powers = targetConfig.getAntennaPowers().clone();
                if (fields.containsKey("antennaPower1")) powers[0] = Integer.parseInt(fields.get("antennaPower1"));
                if (fields.containsKey("antennaPower2")) powers[1] = Integer.parseInt(fields.get("antennaPower2"));
                if (fields.containsKey("antennaPower3")) powers[2] = Integer.parseInt(fields.get("antennaPower3"));
                if (fields.containsKey("antennaPower4")) powers[3] = Integer.parseInt(fields.get("antennaPower4"));
                targetConfig.setAntennaPowers(powers);
            }

            // Save to file
            List<ReaderConfig> allConfigs = new ArrayList<>();
            for (ReaderConnection conn : connections) {
                allConfigs.add(conn.getConfig());
            }
            ReaderConfig.saveToFile(configFile, allConfigs);

            sendOk(exchange, "{\"message\":\"Reader config updated: " + escapeJson(readerName) + "\"}");
        }
    }

    /** GET /api/assets, POST /api/assets, PUT /api/assets/{id} */
    private class AssetsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();

                if ("OPTIONS".equals(method)) {
                    sendJson(exchange, 204, "");
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                String subPath = path.length() > "/api/assets".length()
                    ? path.substring("/api/assets/".length()) : null;

                if ("GET".equals(method) && subPath == null) {
                    handleGetAssets(exchange);
                } else if ("POST".equals(method) && subPath == null) {
                    handlePostAsset(exchange);
                } else if ("PUT".equals(method) && subPath != null) {
                    handlePutAsset(exchange, subPath);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }

        private void handleGetAssets(HttpExchange exchange) throws IOException {
            List<String[]> assets = AssetRepository.getInstance().queryAssets();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < assets.size(); i++) {
                if (i > 0) sb.append(",");
                String[] row = assets.get(i);
                sb.append("{")
                    .append("\"assetNumber\":").append(toJsonString(row[0])).append(",")
                    .append("\"epc\":").append(toJsonString(row[1])).append(",")
                    .append("\"assetName\":").append(toJsonString(row[2])).append(",")
                    .append("\"department\":").append(toJsonString(row[3])).append(",")
                    .append("\"createdAt\":").append(toJsonString(row[4])).append(",")
                    .append("\"possession\":").append("보유".equals(row[5])).append(",")
                    .append("\"id\":").append(row[6])
                    .append("}");
            }
            sb.append("]");
            sendOk(exchange, sb.toString());
        }

        private void handlePostAsset(HttpExchange exchange) throws IOException {
            String body = readRequestBody(exchange);
            Map<String, String> fields = parseJsonFields(body);

            String assetNumber = fields.get("assetNumber");
            String epc = fields.get("epc");
            String assetName = fields.get("assetName");
            String department = fields.get("department");
            String possessionStr = fields.get("possession");

            if (assetNumber == null || assetNumber.isEmpty()) {
                sendError(exchange, 400, "assetNumber is required");
                return;
            }
            if (epc == null || epc.isEmpty()) {
                sendError(exchange, 400, "epc is required");
                return;
            }

            int possession = 1;
            if (possessionStr != null) {
                try {
                    possession = Integer.parseInt(possessionStr);
                } catch (NumberFormatException e) {
                    possession = Boolean.parseBoolean(possessionStr) ? 1 : 0;
                }
            }

            boolean success = AssetRepository.getInstance().insertAsset(assetNumber, epc, assetName, department, possession);
            if (success) {
                AssetRepository.getInstance().refreshCache();
                sendOk(exchange, "{\"message\":\"Asset added\"}");
            } else {
                sendError(exchange, 500, "Failed to insert asset");
            }
        }

        private void handlePutAsset(HttpExchange exchange, String idStr) throws IOException {
            long id;
            try {
                id = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Invalid id: " + idStr);
                return;
            }

            String body = readRequestBody(exchange);
            Map<String, String> fields = parseJsonFields(body);

            String assetNumber = fields.get("assetNumber");
            String epc = fields.get("epc");
            String assetName = fields.get("assetName");
            String department = fields.get("department");
            String possessionStr = fields.get("possession");

            Integer possession = null;
            if (possessionStr != null) {
                try {
                    possession = Integer.parseInt(possessionStr);
                } catch (NumberFormatException e) {
                    possession = Boolean.parseBoolean(possessionStr) ? 1 : 0;
                }
            }

            boolean success = AssetRepository.getInstance().updateAsset(id, assetNumber, epc, assetName, department, possession);
            if (success) {
                AssetRepository.getInstance().refreshCache();
                sendOk(exchange, "{\"message\":\"Asset updated\"}");
            } else {
                sendError(exchange, 500, "Failed to update asset (id=" + id + ")");
            }
        }
    }

    /** GET /api/export-permissions, POST /api/export-permissions, DELETE /api/export-permissions/{id} */
    private class ExportPermissionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();

                if ("OPTIONS".equals(method)) {
                    sendJson(exchange, 204, "");
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                String subPath = path.length() > "/api/export-permissions".length()
                    ? path.substring("/api/export-permissions/".length()) : null;

                if ("GET".equals(method) && subPath == null) {
                    handleGetPermissions(exchange);
                } else if ("POST".equals(method) && subPath == null) {
                    handlePostPermission(exchange);
                } else if ("DELETE".equals(method) && subPath != null) {
                    handleDeletePermission(exchange, subPath);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }

        private void handleGetPermissions(HttpExchange exchange) throws IOException {
            List<String[]> permissions = AssetRepository.getInstance().queryExportPermissions();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < permissions.size(); i++) {
                if (i > 0) sb.append(",");
                String[] row = permissions.get(i);
                sb.append("{")
                    .append("\"epc\":").append(toJsonString(row[0])).append(",")
                    .append("\"assetNumber\":").append(toJsonString(row[1])).append(",")
                    .append("\"assetName\":").append(toJsonString(row[2])).append(",")
                    .append("\"permitStart\":").append(toJsonString(row[3])).append(",")
                    .append("\"permitEnd\":").append(toJsonString(row[4])).append(",")
                    .append("\"reason\":").append(toJsonString(row[5])).append(",")
                    .append("\"status\":").append(toJsonString(row[6])).append(",")
                    .append("\"id\":").append(row.length > 7 ? row[7] : "null")
                    .append("}");
            }
            sb.append("]");
            sendOk(exchange, sb.toString());
        }

        private void handlePostPermission(HttpExchange exchange) throws IOException {
            String body = readRequestBody(exchange);
            Map<String, String> fields = parseJsonFields(body);

            String epc = fields.get("epc");
            String permitStart = fields.get("permitStart");
            String permitEnd = fields.get("permitEnd");
            String reason = fields.get("reason");

            if (epc == null || epc.isEmpty()) {
                sendError(exchange, 400, "epc is required");
                return;
            }
            if (permitStart == null || permitEnd == null) {
                sendError(exchange, 400, "permitStart and permitEnd are required");
                return;
            }

            boolean success = AssetRepository.getInstance().insertPermission(epc, permitStart, permitEnd, reason);
            if (success) {
                AssetRepository.getInstance().refreshCache();
                sendOk(exchange, "{\"message\":\"Permission added\"}");
            } else {
                sendError(exchange, 500, "Failed to insert permission");
            }
        }

        private void handleDeletePermission(HttpExchange exchange, String idStr) throws IOException {
            long id;
            try {
                id = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Invalid id: " + idStr);
                return;
            }

            boolean success = AssetRepository.getInstance().deletePermission(id);
            if (success) {
                AssetRepository.getInstance().refreshCache();
                sendOk(exchange, "{\"message\":\"Permission deleted\"}");
            } else {
                sendError(exchange, 500, "Failed to delete permission (id=" + id + ")");
            }
        }
    }

    /** GET /api/export-alerts?from=...&to=... */
    private class ExportAlertsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    sendJson(exchange, 204, "");
                    return;
                }
                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Method not allowed");
                    return;
                }

                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                String from = params.get("from");
                String to = params.get("to");

                if (from == null || to == null) {
                    sendError(exchange, 400, "from and to parameters are required (format: yyyy-MM-dd HH:mm:ss)");
                    return;
                }

                List<String[]> alerts = AssetRepository.getInstance().queryExportAlerts(from, to);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < alerts.size(); i++) {
                    if (i > 0) sb.append(",");
                    String[] row = alerts.get(i);
                    sb.append("{")
                        .append("\"alertTime\":").append(toJsonString(row[0])).append(",")
                        .append("\"readerName\":").append(toJsonString(row[1])).append(",")
                        .append("\"epc\":").append(toJsonString(row[2])).append(",")
                        .append("\"assetNumber\":").append(toJsonString(row[3])).append(",")
                        .append("\"assetName\":").append(toJsonString(row[4])).append(",")
                        .append("\"department\":").append(toJsonString(row[5])).append(",")
                        .append("\"rssi\":").append(row[6])
                        .append("}");
                }
                sb.append("]");
                sendOk(exchange, sb.toString());
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    /** POST /api/control/{action} - connect-all, disconnect-all, start-inventory, stop-inventory */
    private class ControlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();

                if ("OPTIONS".equals(method)) {
                    sendJson(exchange, 204, "");
                    return;
                }
                if (!"POST".equals(method)) {
                    sendError(exchange, 405, "Method not allowed (use POST)");
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                String action = path.length() > "/api/control/".length()
                    ? path.substring("/api/control/".length()) : "";

                switch (action) {
                    case "connect-all":
                        readerManager.connectAll();
                        sendOk(exchange, "{\"message\":\"Connect all requested\"}");
                        break;
                    case "disconnect-all":
                        readerManager.disconnectAll();
                        sendOk(exchange, "{\"message\":\"Disconnect all requested\"}");
                        break;
                    case "start-inventory":
                        readerManager.startInventoryAll();
                        sendOk(exchange, "{\"message\":\"Start inventory requested\"}");
                        break;
                    case "stop-inventory":
                        readerManager.stopInventoryAll();
                        sendOk(exchange, "{\"message\":\"Stop inventory requested\"}");
                        break;
                    default:
                        sendError(exchange, 404, "Unknown action: " + action
                            + " (available: connect-all, disconnect-all, start-inventory, stop-inventory)");
                }
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    /** GET /swagger - Swagger UI (CDN-based) */
    private class SwaggerUiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = "<!DOCTYPE html>\n"
                + "<html lang=\"ko\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <title>RFID Middleware API - Swagger UI</title>\n"
                + "  <link rel=\"stylesheet\" href=\"https://unpkg.com/swagger-ui-dist@5/swagger-ui.css\">\n"
                + "  <style>html{box-sizing:border-box;overflow-y:scroll}*,*:before,*:after{box-sizing:inherit}"
                + "body{margin:0;background:#fafafa}</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <div id=\"swagger-ui\"></div>\n"
                + "  <script src=\"https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js\"></script>\n"
                + "  <script>\n"
                + "    SwaggerUIBundle({\n"
                + "      url: '/api/openapi.json',\n"
                + "      dom_id: '#swagger-ui',\n"
                + "      deepLinking: true,\n"
                + "      presets: [SwaggerUIBundle.presets.apis, SwaggerUIBundle.SwaggerUIStandalonePreset],\n"
                + "      layout: 'BaseLayout'\n"
                + "    });\n"
                + "  </script>\n"
                + "</body>\n"
                + "</html>";

            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /** GET /api/openapi.json - OpenAPI 3.0 spec */
    private class OpenApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String spec = "{\n"
                + "  \"openapi\": \"3.0.3\",\n"
                + "  \"info\": {\n"
                + "    \"title\": \"RFID Middleware API\",\n"
                + "    \"description\": \"RFID \\ubbf8\\ub4e4\\uc6e8\\uc5b4 \\uc678\\ubd80 \\uc5f0\\ub3d9 REST API\",\n"
                + "    \"version\": \"1.0.0\"\n"
                + "  },\n"
                + "  \"servers\": [{\"url\": \"http://localhost:" + PORT + "\"}],\n"
                + "  \"paths\": {\n"

                // GET /api/readers
                + "    \"/api/readers\": {\n"
                + "      \"get\": {\n"
                + "        \"tags\": [\"Readers\"],\n"
                + "        \"summary\": \"\\ub9ac\\ub354\\uae30 \\uc124\\uc815\\uc815\\ubcf4 \\uc804\\uccb4 \\uc870\\ud68c\",\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\n"
                + "            \"description\": \"\\uc131\\uacf5\",\n"
                + "            \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}\n"
                + "          }\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"

                // PUT /api/readers/{name}
                + "    \"/api/readers/{name}\": {\n"
                + "      \"put\": {\n"
                + "        \"tags\": [\"Readers\"],\n"
                + "        \"summary\": \"\\ub9ac\\ub354\\uae30 \\uc124\\uc815 \\uc218\\uc815\",\n"
                + "        \"parameters\": [{\"name\": \"name\", \"in\": \"path\", \"required\": true, \"schema\": {\"type\": \"string\"}, \"description\": \"\\ub9ac\\ub354\\uae30 \\uc774\\ub984\"}],\n"
                + "        \"requestBody\": {\n"
                + "          \"required\": true,\n"
                + "          \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/ReaderUpdateRequest\"}}}\n"
                + "        },\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}},\n"
                + "          \"404\": {\"description\": \"\\ub9ac\\ub354\\uae30 \\uc5c6\\uc74c\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/ErrorResponse\"}}}}\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"

                // GET/POST /api/assets
                + "    \"/api/assets\": {\n"
                + "      \"get\": {\n"
                + "        \"tags\": [\"Assets\"],\n"
                + "        \"summary\": \"\\uc790\\uc0b0 \\ud14c\\uc774\\ube14 \\uc870\\ud68c\",\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}}\n"
                + "        }\n"
                + "      },\n"
                + "      \"post\": {\n"
                + "        \"tags\": [\"Assets\"],\n"
                + "        \"summary\": \"\\uc790\\uc0b0 \\ucd94\\uac00\",\n"
                + "        \"requestBody\": {\n"
                + "          \"required\": true,\n"
                + "          \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/AssetCreateRequest\"}}}\n"
                + "        },\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}},\n"
                + "          \"400\": {\"description\": \"\\ud544\\uc218 \\ud544\\ub4dc \\ub204\\ub77d\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/ErrorResponse\"}}}}\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"

                // PUT /api/assets/{id}
                + "    \"/api/assets/{id}\": {\n"
                + "      \"put\": {\n"
                + "        \"tags\": [\"Assets\"],\n"
                + "        \"summary\": \"\\uc790\\uc0b0 \\uc218\\uc815\",\n"
                + "        \"parameters\": [{\"name\": \"id\", \"in\": \"path\", \"required\": true, \"schema\": {\"type\": \"integer\", \"format\": \"int64\"}, \"description\": \"\\uc790\\uc0b0 ID\"}],\n"
                + "        \"requestBody\": {\n"
                + "          \"required\": true,\n"
                + "          \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/AssetUpdateRequest\"}}}\n"
                + "        },\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}},\n"
                + "          \"400\": {\"description\": \"\\uc798\\ubabb\\ub41c ID\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/ErrorResponse\"}}}}\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"

                // GET/POST /api/export-permissions
                + "    \"/api/export-permissions\": {\n"
                + "      \"get\": {\n"
                + "        \"tags\": [\"Export Permissions\"],\n"
                + "        \"summary\": \"\\ubc18\\ucd9c\\ud5c8\\uc6a9 \\ubaa9\\ub85d \\uc870\\ud68c\",\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}}\n"
                + "        }\n"
                + "      },\n"
                + "      \"post\": {\n"
                + "        \"tags\": [\"Export Permissions\"],\n"
                + "        \"summary\": \"\\ubc18\\ucd9c\\ud5c8\\uc6a9 \\ucd94\\uac00\",\n"
                + "        \"requestBody\": {\n"
                + "          \"required\": true,\n"
                + "          \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/PermissionCreateRequest\"}}}\n"
                + "        },\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}},\n"
                + "          \"400\": {\"description\": \"\\ud544\\uc218 \\ud544\\ub4dc \\ub204\\ub77d\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/ErrorResponse\"}}}}\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"

                // DELETE /api/export-permissions/{id}
                + "    \"/api/export-permissions/{id}\": {\n"
                + "      \"delete\": {\n"
                + "        \"tags\": [\"Export Permissions\"],\n"
                + "        \"summary\": \"\\ubc18\\ucd9c\\ud5c8\\uc6a9 \\uc0ad\\uc81c\",\n"
                + "        \"parameters\": [{\"name\": \"id\", \"in\": \"path\", \"required\": true, \"schema\": {\"type\": \"integer\", \"format\": \"int64\"}, \"description\": \"\\ubc18\\ucd9c\\ud5c8\\uc6a9 ID\"}],\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}},\n"
                + "          \"400\": {\"description\": \"\\uc798\\ubabb\\ub41c ID\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/ErrorResponse\"}}}}\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"

                // POST /api/control/connect-all
                + "    \"/api/control/connect-all\": {\n"
                + "      \"post\": {\n"
                + "        \"tags\": [\"Control\"],\n"
                + "        \"summary\": \"\\uc804\\uccb4 \\ub9ac\\ub354\\uae30 \\uc5f0\\uacb0\",\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}}\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"

                // POST /api/control/disconnect-all
                + "    \"/api/control/disconnect-all\": {\n"
                + "      \"post\": {\n"
                + "        \"tags\": [\"Control\"],\n"
                + "        \"summary\": \"\\uc804\\uccb4 \\ub9ac\\ub354\\uae30 \\uc5f0\\uacb0 \\ud574\\uc81c\",\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}}\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"

                // POST /api/control/start-inventory
                + "    \"/api/control/start-inventory\": {\n"
                + "      \"post\": {\n"
                + "        \"tags\": [\"Control\"],\n"
                + "        \"summary\": \"\\uc804\\uccb4 \\uc778\\ubca4\\ud1a0\\ub9ac \\uc2dc\\uc791\",\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}}\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"

                // POST /api/control/stop-inventory
                + "    \"/api/control/stop-inventory\": {\n"
                + "      \"post\": {\n"
                + "        \"tags\": [\"Control\"],\n"
                + "        \"summary\": \"\\uc804\\uccb4 \\uc778\\ubca4\\ud1a0\\ub9ac \\uc911\\uc9c0\",\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}}\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"

                // GET /api/export-alerts
                + "    \"/api/export-alerts\": {\n"
                + "      \"get\": {\n"
                + "        \"tags\": [\"Export Alerts\"],\n"
                + "        \"summary\": \"\\ubc18\\ucd9c\\uc54c\\ub9bc \\uc774\\ub825 \\uc870\\ud68c (\\uae30\\uac04 \\uc9c0\\uc815)\",\n"
                + "        \"parameters\": [\n"
                + "          {\"name\": \"from\", \"in\": \"query\", \"required\": true, \"schema\": {\"type\": \"string\"}, \"description\": \"\\uc2dc\\uc791\\uc77c\\uc2dc (yyyy-MM-dd HH:mm:ss)\", \"example\": \"2026-01-01 00:00:00\"},\n"
                + "          {\"name\": \"to\", \"in\": \"query\", \"required\": true, \"schema\": {\"type\": \"string\"}, \"description\": \"\\uc885\\ub8cc\\uc77c\\uc2dc (yyyy-MM-dd HH:mm:ss)\", \"example\": \"2026-12-31 23:59:59\"}\n"
                + "        ],\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}},\n"
                + "          \"400\": {\"description\": \"\\ud544\\uc218 \\ud30c\\ub77c\\ubbf8\\ud130 \\ub204\\ub77d\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/ErrorResponse\"}}}}\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"

                // GET/PUT /api/mask
                + "    \"/api/mask\": {\n"
                + "      \"get\": {\n"
                + "        \"tags\": [\"Settings\"],\n"
                + "        \"summary\": \"EPC Mask \\uc870\\ud68c\",\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}}\n"
                + "        }\n"
                + "      },\n"
                + "      \"put\": {\n"
                + "        \"tags\": [\"Settings\"],\n"
                + "        \"summary\": \"EPC Mask \\uc124\\uc815\",\n"
                + "        \"requestBody\": {\n"
                + "          \"required\": true,\n"
                + "          \"content\": {\"application/json\": {\"schema\": {\"type\": \"object\", \"properties\": {\"mask\": {\"type\": \"string\", \"example\": \"0420\"}}}}}\n"
                + "        },\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}}\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"

                // POST /api/reader-finder/discover
                + "    \"/api/reader-finder/discover\": {\n"
                + "      \"post\": {\n"
                + "        \"tags\": [\"Reader Finder\"],\n"
                + "        \"summary\": \"\\ub124\\ud2b8\\uc6cc\\ud06c \\ub9ac\\ub354\\uae30 \\uac80\\uc0c9\",\n"
                + "        \"description\": \"\\ub124\\ud2b8\\uc6cc\\ud06c\\uc5d0\\uc11c RFID \\ub9ac\\ub354\\uae30\\ub97c \\uac80\\uc0c9\\ud569\\ub2c8\\ub2e4 (\\uc57d 3\\ucd08 \\uc18c\\uc694)\",\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\ubc1c\\uacac\\ub41c \\ub9ac\\ub354\\uae30 \\ubaa9\\ub85d\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}}\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"

                // GET/PUT /api/reader-finder/config/{mac}
                + "    \"/api/reader-finder/config/{mac}\": {\n"
                + "      \"get\": {\n"
                + "        \"tags\": [\"Reader Finder\"],\n"
                + "        \"summary\": \"\\ub9ac\\ub354\\uae30 \\ub124\\ud2b8\\uc6cc\\ud06c \\uc124\\uc815 \\uc870\\ud68c\",\n"
                + "        \"parameters\": [{\"name\": \"mac\", \"in\": \"path\", \"required\": true, \"schema\": {\"type\": \"string\"}, \"description\": \"MAC \\uc8fc\\uc18c\"}],\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}},\n"
                + "          \"404\": {\"description\": \"\\ub9ac\\ub354\\uae30 \\uc5c6\\uc74c\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/ErrorResponse\"}}}}\n"
                + "        }\n"
                + "      },\n"
                + "      \"put\": {\n"
                + "        \"tags\": [\"Reader Finder\"],\n"
                + "        \"summary\": \"\\ub9ac\\ub354\\uae30 IP/\\uc11c\\ube0c\\ub137/\\uac8c\\uc774\\ud2b8\\uc6e8\\uc774 \\uc124\\uc815\",\n"
                + "        \"parameters\": [{\"name\": \"mac\", \"in\": \"path\", \"required\": true, \"schema\": {\"type\": \"string\"}, \"description\": \"MAC \\uc8fc\\uc18c\"}],\n"
                + "        \"requestBody\": {\n"
                + "          \"required\": true,\n"
                + "          \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/ReaderNetworkConfigRequest\"}}}\n"
                + "        },\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}},\n"
                + "          \"400\": {\"description\": \"\\ud544\\uc218 \\ud544\\ub4dc \\ub204\\ub77d\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/ErrorResponse\"}}}}\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"

                // POST /api/reader-finder/restart/{mac}
                + "    \"/api/reader-finder/restart/{mac}\": {\n"
                + "      \"post\": {\n"
                + "        \"tags\": [\"Reader Finder\"],\n"
                + "        \"summary\": \"\\ub9ac\\ub354\\uae30 \\uc7ac\\uc2dc\\uc791\",\n"
                + "        \"parameters\": [{\"name\": \"mac\", \"in\": \"path\", \"required\": true, \"schema\": {\"type\": \"string\"}, \"description\": \"MAC \\uc8fc\\uc18c\"}],\n"
                + "        \"responses\": {\n"
                + "          \"200\": {\"description\": \"\\uc131\\uacf5\", \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/SuccessResponse\"}}}}\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"

                + "  },\n"

                // Components / Schemas
                + "  \"components\": {\n"
                + "    \"schemas\": {\n"

                + "      \"SuccessResponse\": {\n"
                + "        \"type\": \"object\",\n"
                + "        \"properties\": {\n"
                + "          \"status\": {\"type\": \"string\", \"example\": \"ok\"},\n"
                + "          \"data\": {\"type\": \"object\"}\n"
                + "        }\n"
                + "      },\n"

                + "      \"ErrorResponse\": {\n"
                + "        \"type\": \"object\",\n"
                + "        \"properties\": {\n"
                + "          \"status\": {\"type\": \"string\", \"example\": \"error\"},\n"
                + "          \"message\": {\"type\": \"string\"}\n"
                + "        }\n"
                + "      },\n"

                + "      \"ReaderUpdateRequest\": {\n"
                + "        \"type\": \"object\",\n"
                + "        \"properties\": {\n"
                + "          \"ip\": {\"type\": \"string\", \"example\": \"192.168.0.100\"},\n"
                + "          \"port\": {\"type\": \"integer\", \"example\": 14150},\n"
                + "          \"buzzer\": {\"type\": \"boolean\", \"example\": true},\n"
                + "          \"warningLight\": {\"type\": \"boolean\", \"example\": true},\n"
                + "          \"antennaPower1\": {\"type\": \"integer\", \"example\": 30, \"description\": \"\\uc548\\ud14c\\ub098 1 \\ucd9c\\ub825 (dBm)\"},\n"
                + "          \"antennaPower2\": {\"type\": \"integer\", \"example\": 30},\n"
                + "          \"antennaPower3\": {\"type\": \"integer\", \"example\": 30},\n"
                + "          \"antennaPower4\": {\"type\": \"integer\", \"example\": 30},\n"
                + "          \"dwellTime\": {\"type\": \"integer\", \"example\": 500, \"description\": \"\\ub4dc\\uc6f0\\uc2dc\\uac04 (ms)\"},\n"
                + "          \"beepEnabled\": {\"type\": \"boolean\", \"example\": true, \"description\": \"\\ub9ac\\ub354\\uae30 \\ub0b4\\uc7a5 \\ube44\\ud504\\uc74c\"}\n"
                + "        }\n"
                + "      },\n"

                + "      \"PermissionCreateRequest\": {\n"
                + "        \"type\": \"object\",\n"
                + "        \"required\": [\"epc\", \"permitStart\", \"permitEnd\"],\n"
                + "        \"properties\": {\n"
                + "          \"epc\": {\"type\": \"string\", \"example\": \"0420100420250910000006\"},\n"
                + "          \"permitStart\": {\"type\": \"string\", \"example\": \"2026-01-01 00:00:00\", \"description\": \"\\ubc18\\ucd9c\\ud5c8\\uc6a9 \\uc2dc\\uc791\\uc77c\\uc2dc\"},\n"
                + "          \"permitEnd\": {\"type\": \"string\", \"example\": \"2026-12-31 23:59:59\", \"description\": \"\\ubc18\\ucd9c\\ud5c8\\uc6a9 \\uc885\\ub8cc\\uc77c\\uc2dc\"},\n"
                + "          \"reason\": {\"type\": \"string\", \"example\": \"\\uc5c5\\ubb34\\uc6a9 \\ubc18\\ucd9c\", \"description\": \"\\ubc18\\ucd9c \\uc0ac\\uc720\"}\n"
                + "        }\n"
                + "      },\n"

                + "      \"AssetCreateRequest\": {\n"
                + "        \"type\": \"object\",\n"
                + "        \"required\": [\"assetNumber\", \"epc\"],\n"
                + "        \"properties\": {\n"
                + "          \"assetNumber\": {\"type\": \"string\", \"example\": \"A001\"},\n"
                + "          \"epc\": {\"type\": \"string\", \"example\": \"0420100420250910000006\"},\n"
                + "          \"assetName\": {\"type\": \"string\", \"example\": \"\\ubaa8\\ub2c8\\ud130\"},\n"
                + "          \"department\": {\"type\": \"string\", \"example\": \"IT\\ubd80\"},\n"
                + "          \"possession\": {\"type\": \"integer\", \"example\": 1, \"description\": \"1=\\ubcf4\\uc720, 0=\\ubbf8\\ubcf4\\uc720\"}\n"
                + "        }\n"
                + "      },\n"

                + "      \"AssetUpdateRequest\": {\n"
                + "        \"type\": \"object\",\n"
                + "        \"properties\": {\n"
                + "          \"assetNumber\": {\"type\": \"string\", \"example\": \"A001\"},\n"
                + "          \"epc\": {\"type\": \"string\", \"example\": \"0420100420250910000006\"},\n"
                + "          \"assetName\": {\"type\": \"string\", \"example\": \"\\ubaa8\\ub2c8\\ud130 27\\uc778\\uce58\"},\n"
                + "          \"department\": {\"type\": \"string\", \"example\": \"\\uac1c\\ubc1c\\ud300\"},\n"
                + "          \"possession\": {\"type\": \"integer\", \"example\": 0, \"description\": \"1=\\ubcf4\\uc720, 0=\\ubbf8\\ubcf4\\uc720\"}\n"
                + "        }\n"
                + "      },\n"

                + "      \"ReaderNetworkConfigRequest\": {\n"
                + "        \"type\": \"object\",\n"
                + "        \"properties\": {\n"
                + "          \"ip\": {\"type\": \"string\", \"example\": \"192.168.0.100\", \"description\": \"IP \\uc8fc\\uc18c\"},\n"
                + "          \"subnet\": {\"type\": \"string\", \"example\": \"255.255.255.0\", \"description\": \"\\uc11c\\ube0c\\ub137 \\ub9c8\\uc2a4\\ud06c\"},\n"
                + "          \"gateway\": {\"type\": \"string\", \"example\": \"192.168.0.1\", \"description\": \"\\uac8c\\uc774\\ud2b8\\uc6e8\\uc774\"}\n"
                + "        }\n"
                + "      }\n"

                + "    }\n"
                + "  }\n"
                + "}";

            byte[] bytes = spec.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /** GET /api/mask - get EPC mask; PUT /api/mask - set EPC mask */
    private class MaskHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                if ("OPTIONS".equals(method)) {
                    sendJson(exchange, 204, "");
                    return;
                }
                if ("GET".equals(method)) {
                    sendOk(exchange, "{\"mask\":" + toJsonString(ReaderConfig.getEpcMask()) + "}");
                } else if ("PUT".equals(method)) {
                    String body = readRequestBody(exchange);
                    Map<String, String> fields = parseJsonFields(body);
                    String mask = fields.get("mask");
                    ReaderConfig.setEpcMask(mask != null ? mask : "");
                    // Save to config file
                    List<ReaderConfig> allConfigs = new ArrayList<>();
                    for (ReaderConnection conn : readerManager.getConnections()) {
                        allConfigs.add(conn.getConfig());
                    }
                    ReaderConfig.saveToFile(configFile, allConfigs);
                    sendOk(exchange, "{\"mask\":" + toJsonString(ReaderConfig.getEpcMask())
                        + ",\"message\":\"Mask updated\"}");
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    /** GET /api/tags/recent - recent tag data; DELETE /api/tags/recent - clear */
    private class RecentTagsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                if ("OPTIONS".equals(method)) {
                    sendJson(exchange, 204, "");
                    return;
                }
                if ("DELETE".equals(method)) {
                    TagRepository.getInstance().clearRecentTags();
                    sendOk(exchange, "{\"message\":\"Cleared\"}");
                    return;
                }
                if (!"GET".equals(method)) {
                    sendError(exchange, 405, "Method not allowed");
                    return;
                }

                List<TagRepository.RecentTag> tags = TagRepository.getInstance().getRecentTags();
                long seq = TagRepository.getInstance().getRecentTagSeq();
                StringBuilder sb = new StringBuilder("{\"seq\":").append(seq).append(",\"tags\":[");
                for (int i = 0; i < tags.size(); i++) {
                    if (i > 0) sb.append(",");
                    TagRepository.RecentTag t = tags.get(i);
                    sb.append("{")
                        .append("\"time\":").append(toJsonString(t.time)).append(",")
                        .append("\"readerName\":").append(toJsonString(t.readerName)).append(",")
                        .append("\"epc\":").append(toJsonString(t.epc)).append(",")
                        .append("\"rssi\":").append(t.rssi).append(",")
                        .append("\"antenna\":").append(t.antenna).append(",")
                        .append("\"assetNumber\":").append(toJsonString(t.assetNumber)).append(",")
                        .append("\"assetName\":").append(toJsonString(t.assetName)).append(",")
                        .append("\"department\":").append(toJsonString(t.department)).append(",")
                        .append("\"status\":").append(toJsonString(t.status))
                        .append("}");
                }
                sb.append("]}");
                sendOk(exchange, sb.toString());
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    /** POST /api/reader-finder/discover - discover readers on network
     *  GET  /api/reader-finder/config/{mac} - get reader network config
     *  PUT  /api/reader-finder/config/{mac} - set reader IP/subnet/gateway
     *  POST /api/reader-finder/restart/{mac} - restart reader */
    private class ReaderFinderHandler implements HttpHandler {
        private static final short FINDER_PORT = 17776;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                if ("OPTIONS".equals(method)) {
                    sendJson(exchange, 204, "");
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                // /api/reader-finder/discover
                // /api/reader-finder/config/{mac}
                // /api/reader-finder/restart/{mac}
                String subPath = path.substring("/api/reader-finder".length());

                if ("/discover".equals(subPath) && "POST".equals(method)) {
                    handleDiscover(exchange);
                } else if (subPath.startsWith("/config/") && "GET".equals(method)) {
                    String mac = URLDecoder.decode(subPath.substring("/config/".length()), "UTF-8");
                    handleGetConfig(exchange, mac);
                } else if (subPath.startsWith("/config/") && "PUT".equals(method)) {
                    String mac = URLDecoder.decode(subPath.substring("/config/".length()), "UTF-8");
                    handleSetConfig(exchange, mac);
                } else if (subPath.startsWith("/restart/") && "POST".equals(method)) {
                    String mac = URLDecoder.decode(subPath.substring("/restart/".length()), "UTF-8");
                    handleRestart(exchange, mac);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                AppLogger.error("ApiServer", "ReaderFinder error", e);
                sendError(exchange, 500, e.getMessage());
            }
        }

        /** Open finder with retry (port may still be held from previous operation) */
        private int openFinderWithRetry(FixedReaderFinder finder) {
            int result = -1;
            for (int retry = 0; retry < 5; retry++) {
                if (retry > 0) {
                    try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                result = finder.open(FINDER_PORT);
                if (result == 0) return 0;
                AppLogger.info("ReaderFinder", "Retry open (" + (retry + 1) + "/5), error: " + result);
            }
            return result;
        }

        private void handleDiscover(HttpExchange exchange) throws IOException {
            final List<Map<String, String>> devices = Collections.synchronizedList(new ArrayList<>());
            FixedReaderFinder finder = FixedReaderFinder.getInstance();

            try {
                int openResult = openFinderWithRetry(finder);
                if (openResult != 0) {
                    sendError(exchange, 500, "Failed to open reader finder (port " + FINDER_PORT + "), error: " + openResult);
                    return;
                }

                finder.setDeviceDiscoveryCallback((reportData, reportDataLen) -> {
                    String deviceString = new String(reportData, 0, reportDataLen);
                    AppLogger.info("ReaderFinder", "Discovered: " + deviceString);

                    Map<String, String> deviceInfo = new LinkedHashMap<>();
                    String[] parts = deviceString.split(";");
                    for (String part : parts) {
                        String[] kv = part.split("=", 2);
                        if (kv.length == 2) {
                            deviceInfo.put(kv[0].trim(), kv[1].trim());
                        }
                    }
                    if (!deviceInfo.isEmpty()) {
                        devices.add(deviceInfo);
                    }
                });

                finder.discovery();

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                finder.close();
            }

            // Fetch DHCP status: new session for getReaderConfig
            if (!devices.isEmpty()) {
                try {
                    // Retry open with delay (port may not be released immediately)
                    int openResult2 = -1;
                    for (int retry = 0; retry < 5; retry++) {
                        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        openResult2 = finder.open(FINDER_PORT);
                        if (openResult2 == 0) break;
                        AppLogger.info("ReaderFinder", "Retry open (" + (retry + 1) + "/5), error: " + openResult2);
                    }
                    if (openResult2 == 0) {
                        finder.setDeviceDiscoveryCallback((d, l) -> {});
                        finder.discovery();
                        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                        for (Map<String, String> dev : devices) {
                            String mac = dev.getOrDefault("mac_address", dev.getOrDefault("mac", ""));
                            if (!mac.isEmpty()) {
                                try {
                                    String[] config = new String[1];
                                    int result = finder.getReaderConfig(mac, config);
                                    if (result == 0 && config[0] != null) {
                                        for (String part : config[0].split(";")) {
                                            String[] kv = part.split("=", 2);
                                            if (kv.length == 2) {
                                                String key = kv[0].trim();
                                                String val = kv[1].trim();
                                                if ("dhcp".equals(key)) dev.put("dhcp", val);
                                                else if ("port".equals(key)) dev.put("port", val);
                                                else if ("mask".equals(key)) dev.put("subnet", val);
                                                else if ("gateway".equals(key)) dev.put("gateway", val);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    AppLogger.warn("ReaderFinder", "Failed to get config for " + mac + ": " + e.getMessage());
                                }
                            }
                        }
                    }
                } finally {
                    finder.close();
                }
            }

            // Build JSON response
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < devices.size(); i++) {
                if (i > 0) sb.append(",");
                Map<String, String> dev = devices.get(i);
                sb.append("{");
                int j = 0;
                for (Map.Entry<String, String> entry : dev.entrySet()) {
                    if (j > 0) sb.append(",");
                    sb.append(toJsonString(entry.getKey())).append(":").append(toJsonString(entry.getValue()));
                    j++;
                }
                sb.append("}");
            }
            sb.append("]");
            sendOk(exchange, sb.toString());
            AppLogger.info("ReaderFinder", "Discovery complete, found " + devices.size() + " device(s)");
        }

        private void handleGetConfig(HttpExchange exchange, String mac) throws IOException {
            FixedReaderFinder finder = FixedReaderFinder.getInstance();
            try {
                int openResult = openFinderWithRetry(finder);
                if (openResult != 0) {
                    sendError(exchange, 500, "Failed to open reader finder, error: " + openResult);
                    return;
                }

                // Need to discover first so the finder knows about the device
                finder.setDeviceDiscoveryCallback((reportData, reportDataLen) -> {});
                finder.discovery();
                try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                String[] config = new String[1];
                int result = finder.getReaderConfig(mac, config);
                if (result != 0 || config[0] == null) {
                    sendError(exchange, 404, "Failed to get config for MAC: " + mac + ", error: " + result);
                    return;
                }

                // Parse config string like "ip=x;subnet=y;gateway=z;..."
                Map<String, String> configMap = new LinkedHashMap<>();
                configMap.put("mac", mac);
                String[] parts = config[0].split(";");
                for (String part : parts) {
                    String[] kv = part.split("=", 2);
                    if (kv.length == 2) {
                        configMap.put(kv[0].trim(), kv[1].trim());
                    }
                }

                StringBuilder sb = new StringBuilder("{");
                int j = 0;
                for (Map.Entry<String, String> entry : configMap.entrySet()) {
                    if (j > 0) sb.append(",");
                    sb.append(toJsonString(entry.getKey())).append(":").append(toJsonString(entry.getValue()));
                    j++;
                }
                sb.append("}");
                sendOk(exchange, sb.toString());
            } finally {
                finder.close();
            }
        }

        private void handleSetConfig(HttpExchange exchange, String mac) throws IOException {
            String body = readRequestBody(exchange);
            Map<String, String> fields = parseJsonFields(body);

            if (fields.isEmpty()) {
                sendError(exchange, 400, "No config fields provided (dhcp, ip, subnet, gateway)");
                return;
            }

            FixedReaderFinder finder = FixedReaderFinder.getInstance();
            try {
                int openResult = openFinderWithRetry(finder);
                if (openResult != 0) {
                    sendError(exchange, 500, "Failed to open reader finder, error: " + openResult);
                    return;
                }

                // Discover first
                finder.setDeviceDiscoveryCallback((reportData, reportDataLen) -> {});
                finder.discovery();
                try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                // Read current full config first (SDK requires full config string), retry on failure
                String[] currentConfig = new String[1];
                int result = finder.getReaderConfig(mac, currentConfig);
                if (result != 0 || currentConfig[0] == null) {
                    AppLogger.warn("ReaderFinder", "getReaderConfig failed (error: " + result + "), retrying with new session...");
                    finder.close();
                    for (int retry = 0; retry < 3; retry++) {
                        try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        int retryOpen = finder.open(FINDER_PORT);
                        if (retryOpen != 0) continue;
                        finder.setDeviceDiscoveryCallback((d, l) -> {});
                        finder.discovery();
                        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        result = finder.getReaderConfig(mac, currentConfig);
                        if (result == 0 && currentConfig[0] != null) break;
                        AppLogger.warn("ReaderFinder", "getReaderConfig retry " + (retry + 1) + " failed, error: " + result);
                        finder.close();
                    }
                    if (result != 0 || currentConfig[0] == null) {
                        sendError(exchange, 404, "Failed to get current config for MAC: " + mac + ", error: " + result);
                        return;
                    }
                }

                // Parse current config into map
                Map<String, String> configMap = new LinkedHashMap<>();
                for (String part : currentConfig[0].split(";")) {
                    String[] kv = part.split("=", 2);
                    if (kv.length == 2) {
                        configMap.put(kv[0].trim(), kv[1].trim());
                    }
                }

                // Merge requested changes
                if (fields.containsKey("dhcp")) configMap.put("dhcp", fields.get("dhcp"));
                if (fields.containsKey("ip")) configMap.put("ip", fields.get("ip"));
                if (fields.containsKey("subnet")) configMap.put("mask", fields.get("subnet"));
                if (fields.containsKey("gateway")) configMap.put("gateway", fields.get("gateway"));

                // Build full config string
                StringBuilder configStr = new StringBuilder();
                for (Map.Entry<String, String> entry : configMap.entrySet()) {
                    if (configStr.length() > 0) configStr.append(";");
                    configStr.append(entry.getKey()).append("=").append(entry.getValue());
                }

                // Need new session for setReaderConfig (retry open for port release)
                finder.close();
                openResult = -1;
                for (int retry = 0; retry < 5; retry++) {
                    try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    openResult = finder.open(FINDER_PORT);
                    if (openResult == 0) break;
                    AppLogger.info("ReaderFinder", "Retry open for set (" + (retry + 1) + "/5), error: " + openResult);
                }
                if (openResult != 0) {
                    sendError(exchange, 500, "Failed to reopen reader finder, error: " + openResult);
                    return;
                }
                finder.setDeviceDiscoveryCallback((reportData, reportDataLen) -> {});
                finder.discovery();
                try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                result = finder.setReaderConfig(mac, configStr.toString());
                if (result != 0) {
                    sendError(exchange, 500, "Failed to set config for MAC: " + mac + ", error: " + result);
                    return;
                }

                AppLogger.info("ReaderFinder", "Config set for " + mac + ": " + configStr);
                sendOk(exchange, "{\"message\":\"Config updated for " + escapeJson(mac) + "\",\"config\":" + toJsonString(configStr.toString()) + "}");
            } finally {
                finder.close();
            }
        }

        private void handleRestart(HttpExchange exchange, String mac) throws IOException {
            FixedReaderFinder finder = FixedReaderFinder.getInstance();
            try {
                int openResult = openFinderWithRetry(finder);
                if (openResult != 0) {
                    sendError(exchange, 500, "Failed to open reader finder, error: " + openResult);
                    return;
                }

                // Discover first
                finder.setDeviceDiscoveryCallback((reportData, reportDataLen) -> {});
                finder.discovery();
                try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                int result = finder.restartReader(mac);
                if (result != 0) {
                    sendError(exchange, 500, "Failed to restart reader: " + mac + ", error: " + result);
                    return;
                }

                AppLogger.info("ReaderFinder", "Restart requested for " + mac);
                sendOk(exchange, "{\"message\":\"Restart requested for " + escapeJson(mac) + "\"}");
            } finally {
                finder.close();
            }
        }
    }

    /** GET / - Dashboard HTML UI */
    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"/".equals(exchange.getRequestURI().getPath())) {
                sendError(exchange, 404, "Not found");
                return;
            }
            byte[] bytes = DashboardHtml.getHtml().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
