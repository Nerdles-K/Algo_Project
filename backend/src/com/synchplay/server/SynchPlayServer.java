package com.synchplay.server;

import com.sun.net.httpserver.*;
import com.synchplay.model.*;
import com.synchplay.loader.*;
import com.synchplay.db.*;
import com.synchplay.service.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * SynchPlay REST API 服务器（纯后端，不包含前端静态文件）
 */
public class SynchPlayServer {
    private final HttpServer server;
    private Graph graph;
    private final DatabaseManager db;
    private final long startTime;
    private FriendRecommendation friendRec;

    public SynchPlayServer(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.db = new DatabaseManager();
        this.startTime = System.currentTimeMillis();
    }

    public void start() throws Exception {
        System.out.println("[启动] 初始化 SQLite 数据库...");
        db.initialize();
        db.importFromCSV("../ProcessedData/mini_nodes.csv", "../ProcessedData/mini_edges.csv");
        System.out.println("[启动] 数据库就绪");

        System.out.println("[启动] 加载图数据...");
        this.graph = DataLoader.loadGraphSilently();
        this.friendRec = new FriendRecommendation(graph);
        System.out.println("[启动] 图数据就绪 (" + graph.getNodeCount() + " 节点, " + graph.getEdgeCount() + " 边)");

        // 注册 API 路由
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/friends", new FriendsHandler());
        server.createContext("/api/stats", new StatsHandler());
        server.createContext("/api/users", new UsersHandler());
        server.createContext("/api/recommend", new RecommendHandler());
        server.createContext("/api/lcc", new LccHandler());
        server.createContext("/api/pagerank", new PageRankHandler());
        server.createContext("/", new RootHandler());

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("\n  SynchPlay API 服务已启动: http://localhost:" + server.getAddress().getPort());
        System.out.println("  健康检查: http://localhost:" + server.getAddress().getPort() + "/api/health");
        System.out.println("  前端: 浏览器打开 ../frontend/index.html");
    }

    /** 记录每次请求的方法、路径、状态码、耗时 */
    private void logRequest(HttpExchange ex, int status, long startMs) {
        long duration = System.currentTimeMillis() - startMs;
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery();
        System.out.printf("[%tT] %s %s%s → %d (%dms)%n",
            System.currentTimeMillis(), ex.getRequestMethod(),
            path, query != null ? "?" + query : "", status, duration);
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    // ── JSON 工具 ──
    private static String jsonEscape(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        addCors(exchange);
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    // ── API 处理器 ──

    class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCors(ex); ex.sendResponseHeaders(204, -1); return; }
            long uptime = (System.currentTimeMillis() - startTime) / 1000;
            String json = String.format(
                "{\"status\":\"ok\",\"uptimeSeconds\":%d,\"nodes\":%d,\"edges\":%d,\"users\":%d,\"videos\":%d}",
                uptime, graph.getNodeCount(), graph.getEdgeCount(),
                graph.getUserNodes().size(), graph.getVideoNodes().size());
            sendJson(ex, 200, json);
        }
    }

    class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCors(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                Map<String, Object> dbStats = db.getStats();
                StringBuilder json = new StringBuilder("{");
                json.append("\"graph\":{");
                json.append("\"totalNodes\":").append(graph.getNodeCount()).append(",");
                json.append("\"userNodes\":").append(graph.getUserNodes().size()).append(",");
                json.append("\"videoNodes\":").append(graph.getVideoNodes().size()).append(",");
                json.append("\"totalEdges\":").append(graph.getEdgeCount()).append(",");
                json.append("\"density\":").append(String.format("%.4f", graph.getDensity())).append(",");
                json.append("\"avgDegree\":").append(String.format("%.2f", graph.getAverageDegree()));
                json.append("},");
                json.append("\"database\":{");
                json.append("\"totalNodes\":").append(dbStats.get("totalNodes")).append(",");
                json.append("\"totalEdges\":").append(dbStats.get("totalEdges"));
                json.append("},");
                json.append("\"edgeTypes\":{");
                json.append("\"social\":").append(dbStats.get("socialEdges")).append(",");
                json.append("\"watch\":").append(dbStats.get("watchEdges")).append(",");
                json.append("\"similar\":").append(dbStats.get("similarEdges"));
                json.append("}}");
                sendJson(ex, 200, json.toString());
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    class UsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCors(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                List<Map<String, Object>> users = db.getUsers();
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < users.size(); i++) {
                    if (i > 0) json.append(",");
                    Map<String, Object> u = users.get(i);
                    json.append("{\"id\":").append(jsonEscape((String) u.get("node_id")));
                    json.append(",\"name\":").append(jsonEscape((String) u.get("display_name")));
                    json.append("}");
                }
                json.append("]");
                sendJson(ex, 200, json.toString());
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    class RecommendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCors(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
                String userId = params.getOrDefault("userId", "");
                double alpha = Double.parseDouble(params.getOrDefault("alpha", "0.4"));
                double beta = Double.parseDouble(params.getOrDefault("beta", "0.6"));
                String prMode = params.getOrDefault("prMode", "full");

                if (userId.isEmpty() || graph.getNode(userId) == null) {
                    sendJson(ex, 400, "{\"error\":\"Invalid userId\"}");
                    return;
                }

                List<Graph.VideoScore> results = graph.rankCandidatesByCompositeScore(userId, alpha, beta, prMode);
                StringBuilder json = new StringBuilder();
                json.append("{\"userId\":").append(jsonEscape(userId));
                json.append(",\"prMode\":").append(jsonEscape(prMode));
                json.append(",\"alpha\":").append(alpha);
                json.append(",\"beta\":").append(beta);
                json.append(",\"totalCandidates\":").append(results.size());
                json.append(",\"recommendations\":[");
                int limit = Math.min(20, results.size());
                for (int i = 0; i < limit; i++) {
                    if (i > 0) json.append(",");
                    Graph.VideoScore vs = results.get(i);
                    json.append("{");
                    json.append("\"rank\":").append(i + 1);
                    json.append(",\"videoId\":").append(jsonEscape(vs.video.getNodeId()));
                    json.append(",\"title\":").append(jsonEscape(vs.video.getDisplayName()));
                    String channel = vs.video.getAttribute("channel");
                    json.append(",\"channel\":").append(jsonEscape(channel != null ? channel : ""));
                    String views = vs.video.getAttribute("views");
                    json.append(",\"views\":").append(views != null ? views : "0");
                    json.append(",\"distance\":").append(vs.distance);
                    json.append(",\"pageRankScore\":").append(String.format("%.6f", vs.pageRankScore));
                    json.append(",\"finalScore\":").append(String.format("%.6f", vs.finalScore));
                    json.append("}");
                }
                json.append("]}");
                sendJson(ex, 200, json.toString());
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    class LccHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCors(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                LinkedHashMap<Node, Double> allLcc = graph.computeAllUserLCC();
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                for (Map.Entry<Node, Double> entry : allLcc.entrySet()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{\"userId\":").append(jsonEscape(entry.getKey().getNodeId()));
                    json.append(",\"name\":").append(jsonEscape(entry.getKey().getDisplayName()));
                    json.append(",\"lcc\":").append(String.format("%.4f", entry.getValue()));
                    json.append(",\"riskLevel\":").append(jsonEscape(
                        entry.getValue() > 0.7 ? "high" : entry.getValue() > 0.3 ? "medium" : "low"));
                    json.append("}");
                }
                json.append("]");
                sendJson(ex, 200, json.toString());
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    class PageRankHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCors(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
                int top = Integer.parseInt(params.getOrDefault("top", "10"));

                LinkedHashMap<Node, Double> ranks = graph.getVideoPageRankScores(20, 0.85);
                StringBuilder json = new StringBuilder("[");
                int count = 0;
                for (Map.Entry<Node, Double> entry : ranks.entrySet()) {
                    if (count >= top) break;
                    if (count > 0) json.append(",");
                    Node video = entry.getKey();
                    json.append("{\"rank\":").append(count + 1);
                    json.append(",\"videoId\":").append(jsonEscape(video.getNodeId()));
                    json.append(",\"title\":").append(jsonEscape(video.getDisplayName()));
                    String channel = video.getAttribute("channel");
                    json.append(",\"channel\":").append(jsonEscape(channel != null ? channel : ""));
                    json.append(",\"score\":").append(String.format("%.6f", entry.getValue()));
                    json.append("}");
                    count++;
                }
                json.append("]");
                sendJson(ex, 200, json.toString());
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    class FriendsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCors(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
                String userId = params.getOrDefault("userId", "");

                if (userId.isEmpty() || graph.getNode(userId) == null) {
                    sendJson(ex, 400, "{\"error\":\"Invalid userId\"}");
                    return;
                }

                List<Node> friends = friendRec.recommend(userId);
                StringBuilder json = new StringBuilder();
                json.append("{\"userId\":").append(jsonEscape(userId));
                json.append(",\"totalRecommended\":").append(friends.size());
                json.append(",\"friends\":[");
                for (int i = 0; i < Math.min(20, friends.size()); i++) {
                    if (i > 0) json.append(",");
                    Node f = friends.get(i);
                    json.append("{\"rank\":").append(i + 1);
                    json.append(",\"userId\":").append(jsonEscape(f.getNodeId()));
                    json.append(",\"name\":").append(jsonEscape(f.getDisplayName()));
                    json.append("}");
                }
                json.append("]}");
                sendJson(ex, 200, json.toString());
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    /** 静态文件服务：提供前端 HTML/CSS/JS 文件 */
    class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            java.nio.file.Path filePath = java.nio.file.Paths.get("../frontend", path);
            if (!java.nio.file.Files.exists(filePath) || java.nio.file.Files.isDirectory(filePath)) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(filePath);
            String contentType = path.endsWith(".css") ? "text/css" :
                                path.endsWith(".js") ? "application/javascript" :
                                "text/html; charset=utf-8";
            addCors(ex);
            ex.getResponseHeaders().set("Content-Type", contentType);
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.getResponseBody().close();
        }
    }
}
