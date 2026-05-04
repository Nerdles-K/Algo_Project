package com.synchplay.db;

import java.sql.*;
import java.io.*;
import java.util.*;

/**
 * SQLite 数据库管理层
 * 负责建表、CSV导入、数据查询
 */
public class DatabaseManager {
    private static final String DB_PATH = "ProcessedData/synchplay.db";
    private Connection conn;

    public void initialize() throws SQLException {
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException e) { throw new SQLException("SQLite JDBC driver not found", e); }
        conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        createTables();
    }

    private void createTables() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS nodes (" +
                "node_id TEXT PRIMARY KEY, node_type TEXT NOT NULL, " +
                "original_id TEXT, display_name TEXT, channel TEXT, " +
                "views INTEGER DEFAULT 0, likes INTEGER DEFAULT 0)");
            stmt.execute("CREATE TABLE IF NOT EXISTS edges (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, source TEXT NOT NULL, " +
                "target TEXT NOT NULL, edge_type TEXT NOT NULL, " +
                "weight REAL DEFAULT 1.0)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_edges_source ON edges(source)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_edges_target ON edges(target)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_edges_type ON edges(edge_type)");
        }
    }

    public boolean isDataLoaded() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM nodes")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    public void importFromCSV(String nodesFile, String edgesFile) throws SQLException, IOException {
        if (isDataLoaded()) return;

        conn.setAutoCommit(false);

        // 导入节点
        try (PreparedStatement psNode = conn.prepareStatement(
                "INSERT OR IGNORE INTO nodes VALUES (?,?,?,?,?,?,?)");
             BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(nodesFile), "UTF-8"))) {
            reader.readLine(); // 跳过表头
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = parseCsvLine(line);
                psNode.setString(1, parts[0]); // node_id
                psNode.setString(2, parts[1]); // node_type
                psNode.setString(3, parts[2]); // original_id
                psNode.setString(4, parts[3]); // display_name
                psNode.setString(5, parts.length > 4 ? parts[4] : "");
                psNode.setInt(6, parts.length > 5 && !parts[5].isEmpty() ? Integer.parseInt(parts[5]) : 0);
                psNode.setInt(7, parts.length > 6 && !parts[6].isEmpty() ? Integer.parseInt(parts[6]) : 0);
                psNode.executeUpdate();
            }
        }

        // 导入边
        try (PreparedStatement psEdge = conn.prepareStatement(
                "INSERT INTO edges (source,target,edge_type,weight) VALUES (?,?,?,?)");
             BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(edgesFile), "UTF-8"))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = parseCsvLine(line);
                psEdge.setString(1, parts[0]);
                psEdge.setString(2, parts[1]);
                psEdge.setString(3, parts[2]);
                psEdge.setDouble(4, Double.parseDouble(parts[3]));
                psEdge.executeUpdate();
            }
        }

        conn.commit();
        conn.setAutoCommit(true);
    }

    public Map<String, Object> getStats() throws SQLException {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalNodes", querySingleInt("SELECT COUNT(*) FROM nodes"));
        stats.put("userNodes", querySingleInt("SELECT COUNT(*) FROM nodes WHERE node_type='user'"));
        stats.put("videoNodes", querySingleInt("SELECT COUNT(*) FROM nodes WHERE node_type='video'"));
        stats.put("totalEdges", querySingleInt("SELECT COUNT(*) FROM edges"));
        stats.put("socialEdges", querySingleInt("SELECT COUNT(*) FROM edges WHERE edge_type='social'"));
        stats.put("watchEdges", querySingleInt("SELECT COUNT(*) FROM edges WHERE edge_type='watch'"));
        stats.put("similarEdges", querySingleInt("SELECT COUNT(*) FROM edges WHERE edge_type='similar'"));
        return stats;
    }

    public List<Map<String, Object>> getUsers() throws SQLException {
        return queryList("SELECT node_id, original_id, display_name FROM nodes WHERE node_type='user' ORDER BY node_id");
    }

    public List<Map<String, Object>> getUserNeighbors(String userId) throws SQLException {
        return queryList(
            "SELECT n.node_id, n.display_name, n.node_type, e.edge_type, e.weight " +
            "FROM edges e JOIN nodes n ON (e.target = n.node_id) " +
            "WHERE e.source = ? UNION " +
            "SELECT n.node_id, n.display_name, n.node_type, e.edge_type, e.weight " +
            "FROM edges e JOIN nodes n ON (e.source = n.node_id) " +
            "WHERE e.target = ?", userId, userId);
    }

    public List<Map<String, Object>> getTopVideos(int limit) throws SQLException {
        return queryList(
            "SELECT node_id, display_name, channel, views, likes FROM nodes " +
            "WHERE node_type='video' ORDER BY views DESC LIMIT ?", limit);
    }

    private int querySingleInt(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof String) ps.setString(i + 1, (String) params[i]);
                else if (params[i] instanceof Integer) ps.setInt(i + 1, (Integer) params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private List<Map<String, Object>> queryList(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof String) ps.setString(i + 1, (String) params[i]);
                else if (params[i] instanceof Integer) ps.setInt(i + 1, (Integer) params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    results.add(row);
                }
            }
        }
        return results;
    }

    public void close() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }
}
