package com.synchplay.loader;

import com.synchplay.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 数据加载器
 * 从CSV文件中读取节点和边，构建异构图
 */
public class DataLoader {
    private static final String NODES_FILE = "ProcessedData/mini_nodes.csv";
    private static final String EDGES_FILE = "ProcessedData/mini_edges.csv";

    /**
     * 加载图数据
     * 返回构建好的Graph对象
     */
    public static Graph loadGraph() throws IOException {
        Graph graph = new Graph();

        System.out.println("====== 数据加载开始 ======");

        // 第一步：加载节点
        System.out.println("\n[第1步] 加载节点...");
        loadNodes(graph);

        // 第二步：加载边
        System.out.println("\n[第2步] 加载边...");
        loadEdges(graph);

        System.out.println("\n====== 数据加载完成 ======");
        graph.printStatistics();

        return graph;
    }

    /**
     * 从CSV加载节点
     */
    private static void loadNodes(Graph graph) throws IOException {
        File file = new File(NODES_FILE);
        if (!file.exists()) {
            throw new FileNotFoundException("节点文件不存在: " + NODES_FILE);
        }

        int nodeCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;

                // 跳过标题行
                if (lineNum == 1) continue;

                String[] parts = parseCsvLine(line);
                if (parts.length < 4) {
                    System.err.println("警告: 第 " + lineNum + " 行数据格式不正确，跳过");
                    continue;
                }

                String nodeId = parts[0].trim();
                String nodeType = parts[1].trim();
                String originalId = parts[2].trim();
                String displayName = parts[3].trim();

                // 创建节点
                Node node = new Node(nodeId, nodeType, originalId, displayName);

                // 添加其他属性
                if (parts.length > 4 && !parts[4].trim().isEmpty()) {
                    node.setAttribute("channel", parts[4].trim());
                }
                if (parts.length > 5 && !parts[5].trim().isEmpty()) {
                    node.setAttribute("views", parts[5].trim());
                }
                if (parts.length > 6 && !parts[6].trim().isEmpty()) {
                    node.setAttribute("likes", parts[6].trim());
                }

                graph.addNode(node);
                nodeCount++;
            }
        }

        System.out.println("✓ 加载了 " + nodeCount + " 个节点");
    }

    /**
     * 从CSV加载边
     */
    private static void loadEdges(Graph graph) throws IOException {
        File file = new File(EDGES_FILE);
        if (!file.exists()) {
            throw new FileNotFoundException("边文件不存在: " + EDGES_FILE);
        }

        int edgeCount = 0;
        int skipCount = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;

                // 跳过标题行
                if (lineNum == 1) continue;

                String[] parts = parseCsvLine(line);
                if (parts.length < 4) {
                    System.err.println("警告: 第 " + lineNum + " 行数据格式不正确，跳过");
                    continue;
                }

                String sourceId = parts[0].trim();
                String targetId = parts[1].trim();
                String edgeType = parts[2].trim();
                double weight;

                try {
                    weight = Double.parseDouble(parts[3].trim());
                } catch (NumberFormatException e) {
                    System.err.println("警告: 第 " + lineNum + " 行权重格式错误，跳过");
                    continue;
                }

                // 获取节点
                Node source = graph.getNode(sourceId);
                Node target = graph.getNode(targetId);

                if (source == null || target == null) {
                    skipCount++;
                    continue;
                }

                // 创建和添加边
                Edge edge = new Edge(source, target, edgeType, weight);
                graph.addEdge(edge);
                edgeCount++;
            }
        }

        System.out.println("✓ 加载了 " + edgeCount + " 条边");
        if (skipCount > 0) {
            System.out.println("⚠ 跳过了 " + skipCount + " 条边（源节点或目标节点不存在）");
        }
    }

    /**
     * 解析CSV行
     * 处理带有引号的字段
     */
    private static String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());

        return result.toArray(new String[0]);
    }

    /**
     * 验证数据文件是否存在
     */
    public static void validateDataFiles() throws IOException {
        File nodesFile = new File(NODES_FILE);
        File edgesFile = new File(EDGES_FILE);

        if (!nodesFile.exists()) {
            throw new FileNotFoundException("缺少节点文件: " + NODES_FILE);
        }
        if (!edgesFile.exists()) {
            throw new FileNotFoundException("缺少边文件: " + EDGES_FILE);
        }

        System.out.println("✓ 数据文件验证通过");
        System.out.println("  - " + nodesFile.getAbsolutePath());
        System.out.println("  - " + edgesFile.getAbsolutePath());
    }

    /**
     * 静默加载图数据（不打印日志，用于服务端模式）
     */
    public static Graph loadGraphSilently() throws IOException {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try {
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            System.setErr(new PrintStream(OutputStream.nullOutputStream()));
            return loadGraph();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
