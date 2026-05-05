package com.synchplay.model;

import java.util.*;

/**
 * 异构图类
 * 管理所有的节点和边
 */
public class Graph {
    private Map<String, Node> nodeMap;  // 所有节点，key为nodeId
    private List<Edge> edges;           // 所有边
    private Map<String, List<Edge>> adjacencyList;  // 邻接表：nodeId -> 出边列表
    private Map<String, List<Edge>> reverseAdjacencyList; // 反向邻接表：nodeId -> 入边列表

    public Graph() {
        this.nodeMap = new HashMap<>();
        this.edges = new ArrayList<>();
        this.adjacencyList = new HashMap<>();
        this.reverseAdjacencyList = new HashMap<>();
    }

    // ============== 节点操作 ==============

    /**
     * 添加节点
     */
    public void addNode(Node node) {
        nodeMap.put(node.getNodeId(), node);
        adjacencyList.putIfAbsent(node.getNodeId(), new ArrayList<>());
        reverseAdjacencyList.putIfAbsent(node.getNodeId(), new ArrayList<>());
    }

    /**
     * 获取节点
     */
    public Node getNode(String nodeId) {
        return nodeMap.get(nodeId);
    }

    /**
     * 获取所有节点
     */
    public Collection<Node> getAllNodes() {
        return nodeMap.values();
    }

    /**
     * 获取所有用户节点
     */
    public List<Node> getUserNodes() {
        List<Node> users = new ArrayList<>();
        for (Node node : nodeMap.values()) {
            if (node.isUser()) {
                users.add(node);
            }
        }
        return users;
    }

    /**
     * 获取所有视频节点
     */
    public List<Node> getVideoNodes() {
        List<Node> videos = new ArrayList<>();
        for (Node node : nodeMap.values()) {
            if (node.isVideo()) {
                videos.add(node);
            }
        }
        return videos;
    }

    /**
     * 获取节点总数
     */
    public int getNodeCount() {
        return nodeMap.size();
    }

    // ============== 边操作 ==============

    /**
     * 添加边
     */
    public void addEdge(Edge edge) {
        edges.add(edge);

        // 更新邻接表
        adjacencyList.putIfAbsent(edge.getSource().getNodeId(), new ArrayList<>());
        adjacencyList.get(edge.getSource().getNodeId()).add(edge);
        reverseAdjacencyList.putIfAbsent(edge.getTarget().getNodeId(), new ArrayList<>());
        reverseAdjacencyList.get(edge.getTarget().getNodeId()).add(edge);

        // 更新节点的邻接表
        edge.getSource().addNeighbor(edge.getTarget());
    }

    /**
     * 获取节点的出边列表
     */
    public List<Edge> getOutEdges(String nodeId) {
        return adjacencyList.getOrDefault(nodeId, new ArrayList<>());
    }

    /**
     * 获取节点的入边列表
     */
    public List<Edge> getInEdges(String nodeId) {
        return reverseAdjacencyList.getOrDefault(nodeId, new ArrayList<>());
    }

    /**
     * 获取所有边
     */
    public List<Edge> getAllEdges() {
        return edges;
    }

    /**
     * 获取边总数
     */
    public int getEdgeCount() {
        return edges.size();
    }

    /**
     * 按边类型获取边
     */
    public List<Edge> getEdgesByType(String edgeType) {
        List<Edge> result = new ArrayList<>();
        for (Edge edge : edges) {
            if (edgeType.equals(edge.getEdgeType())) {
                result.add(edge);
            }
        }
        return result;
    }

    // ============== 图算法 ==============

    /**
     * BFS召回：从用户出发，在K-hop内寻找候选视频
     */
    public List<Node> findCandidateVideosByBFS(String userNodeId, int maxDepth) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth 必须 >= 1");
        }

        Node startNode = getNode(userNodeId);
        if (startNode == null) {
            throw new IllegalArgumentException("用户节点不存在: " + userNodeId);
        }
        if (!startNode.isUser()) {
            throw new IllegalArgumentException("起始节点不是用户: " + userNodeId);
        }

        Queue<BFSState> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        LinkedHashSet<String> candidateVideoIds = new LinkedHashSet<>();

        queue.offer(new BFSState(userNodeId, 0));
        visited.add(userNodeId);

        while (!queue.isEmpty()) {
            BFSState current = queue.poll();

            if (current.depth >= maxDepth) {
                continue;
            }

            for (Node neighbor : getUndirectedNeighbors(current.nodeId)) {
                String neighborId = neighbor.getNodeId();
                if (!visited.add(neighborId)) {
                    continue;
                }

                int nextDepth = current.depth + 1;
                queue.offer(new BFSState(neighborId, nextDepth));

                if (neighbor.isVideo()) {
                    candidateVideoIds.add(neighborId);
                }
            }
        }

        List<Node> candidates = new ArrayList<>();
        for (String videoId : candidateVideoIds) {
            Node videoNode = getNode(videoId);
            if (videoNode != null && videoNode.isVideo()) {
                candidates.add(videoNode);
            }
        }
        return candidates;
    }

    /**
     * 计算全图PageRank分数
     */
    public Map<String, Double> computePageRank(int iterations, double dampingFactor) {
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations 必须 > 0");
        }
        if (dampingFactor <= 0.0 || dampingFactor >= 1.0) {
            throw new IllegalArgumentException("dampingFactor 必须在 (0, 1) 之间");
        }
        if (nodeMap.isEmpty()) {
            return Collections.emptyMap();
        }

        int nodeCount = nodeMap.size();
        double initRank = 1.0 / nodeCount;
        Map<String, Double> ranks = new HashMap<>();

        for (String nodeId : nodeMap.keySet()) {
            ranks.put(nodeId, initRank);
        }

        for (int i = 0; i < iterations; i++) {
            Map<String, Double> nextRanks = new HashMap<>();
            double baseRank = (1.0 - dampingFactor) / nodeCount;

            for (String nodeId : nodeMap.keySet()) {
                nextRanks.put(nodeId, baseRank);
            }

            double sinkRankSum = 0.0;
            for (String nodeId : nodeMap.keySet()) {
                int outDegree = getOutDegree(nodeId);
                if (outDegree == 0) {
                    sinkRankSum += ranks.get(nodeId);
                    continue;
                }

                double distributedRank = dampingFactor * ranks.get(nodeId) / outDegree;
                for (Edge edge : getOutEdges(nodeId)) {
                    String targetId = edge.getTarget().getNodeId();
                    nextRanks.put(targetId, nextRanks.get(targetId) + distributedRank);
                }
            }

            double sinkContribution = dampingFactor * sinkRankSum / nodeCount;
            for (String nodeId : nodeMap.keySet()) {
                nextRanks.put(nodeId, nextRanks.get(nodeId) + sinkContribution);
            }

            ranks = nextRanks;
        }

        return ranks;
    }

    /**
     * 获取视频节点的PageRank（按分数降序）
     */
    public LinkedHashMap<Node, Double> getVideoPageRankScores(int iterations, double dampingFactor) {
        Map<String, Double> rankMap = computePageRank(iterations, dampingFactor);
        return sortVideoRanks(rankMap);
    }

    /**
     * 基于观看行为的PageRank（对应 pagerank.py 的算法）
     * 仅考虑 user → video 的 watch 边，每个用户将其"投票权"均分给看过的视频
     * 收敛式迭代：maxIter 最大迭代次数，tol 收敛阈值
     */
    public Map<String, Double> computeWatchBasedPageRank(double alpha, int maxIter, double tol) {
        List<Node> videos = getVideoNodes();
        int n = videos.size();
        if (n == 0) return Collections.emptyMap();

        // 初始化
        Map<String, Double> pr = new HashMap<>();
        double initRank = 1.0 / n;
        for (Node v : videos) {
            pr.put(v.getNodeId(), initRank);
        }

        // 预计算每个用户看的视频列表（只考虑 watch 边）
        Map<String, List<String>> userWatchedVideos = new HashMap<>();
        for (Edge edge : getEdgesByType("watch")) {
            String userId = edge.getSource().getNodeId();
            String videoId = edge.getTarget().getNodeId();
            userWatchedVideos.computeIfAbsent(userId, k -> new ArrayList<>()).add(videoId);
        }

        for (int iter = 0; iter < maxIter; iter++) {
            Map<String, Double> newPr = new HashMap<>();
            double baseRank = (1.0 - alpha) / n;
            for (Node v : videos) {
                newPr.put(v.getNodeId(), baseRank);
            }

            // 每个用户将 alpha * 1.0 均分给看过的视频
            for (Map.Entry<String, List<String>> entry : userWatchedVideos.entrySet()) {
                List<String> watched = entry.getValue();
                if (watched.isEmpty()) continue;
                double share = alpha / watched.size();
                for (String videoId : watched) {
                    newPr.put(videoId, newPr.getOrDefault(videoId, 0.0) + share);
                }
            }

            // 归一化
            double total = 0.0;
            for (double val : newPr.values()) total += val;
            if (total > 0) {
                for (String vid : newPr.keySet()) {
                    newPr.put(vid, newPr.get(vid) / total);
                }
            }

            // 检查收敛
            double diff = 0.0;
            for (String vid : pr.keySet()) {
                diff += Math.abs(newPr.getOrDefault(vid, 0.0) - pr.get(vid));
            }
            pr = newPr;
            if (diff < tol) break;
        }
        return pr;
    }

    /**
     * 获取视频节点的Watch-Based PageRank（按分数降序）
     */
    public LinkedHashMap<Node, Double> getVideoWatchBasedPageRankScores(double alpha, int maxIter, double tol) {
        Map<String, Double> rankMap = computeWatchBasedPageRank(alpha, maxIter, tol);
        return sortVideoRanks(rankMap);
    }

    private LinkedHashMap<Node, Double> sortVideoRanks(Map<String, Double> rankMap) {
        List<Map.Entry<Node, Double>> entries = new ArrayList<>();
        for (Node node : nodeMap.values()) {
            if (node.isVideo()) {
                entries.add(new AbstractMap.SimpleEntry<>(node, rankMap.getOrDefault(node.getNodeId(), 0.0)));
            }
        }
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        LinkedHashMap<Node, Double> result = new LinkedHashMap<>();
        for (Map.Entry<Node, Double> entry : entries) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    // ============== LCC 茧房检测 ==============

    /**
     * 计算用户的局部聚类系数 (LCC)
     * 衡量用户的社交邻居之间相互连接的程度
     * LCC ∈ [0,1]，越高表示社交圈越封闭（茧房效应越强）
     */
    public double computeLocalClusteringCoefficient(String userNodeId) {
        Node user = getNode(userNodeId);
        if (user == null || !user.isUser()) {
            return 0.0;
        }

        // 获取该用户的所有社交邻居（通过social边连接的其他用户）
        Set<String> socialNeighbors = new HashSet<>();
        for (Edge edge : getOutEdges(userNodeId)) {
            if ("social".equals(edge.getEdgeType()) && edge.getTarget().isUser()) {
                socialNeighbors.add(edge.getTarget().getNodeId());
            }
        }
        for (Edge edge : getInEdges(userNodeId)) {
            if ("social".equals(edge.getEdgeType()) && edge.getSource().isUser()) {
                socialNeighbors.add(edge.getSource().getNodeId());
            }
        }

        int k = socialNeighbors.size();
        if (k < 2) {
            return k == 0 ? 0.0 : 0.0; // 邻居少于2，LCC为0
        }

        // 统计邻居之间实际存在的边数
        int edgesBetweenNeighbors = 0;
        List<String> neighborList = new ArrayList<>(socialNeighbors);
        for (int i = 0; i < neighborList.size(); i++) {
            String ni = neighborList.get(i);
            for (int j = i + 1; j < neighborList.size(); j++) {
                String nj = neighborList.get(j);
                // 检查ni和nj之间是否有social边（任一方向）
                if (hasSocialEdgeBetween(ni, nj)) {
                    edgesBetweenNeighbors++;
                }
            }
        }

        // LCC = 2 * 邻居间边数 / (k * (k-1))
        return (2.0 * edgesBetweenNeighbors) / (k * (k - 1.0));
    }

    /**
     * 计算所有用户的LCC，按LCC降序返回
     */
    public LinkedHashMap<Node, Double> computeAllUserLCC() {
        List<Map.Entry<Node, Double>> entries = new ArrayList<>();
        for (Node user : getUserNodes()) {
            double lcc = computeLocalClusteringCoefficient(user.getNodeId());
            entries.add(new AbstractMap.SimpleEntry<>(user, lcc));
        }
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        LinkedHashMap<Node, Double> result = new LinkedHashMap<>();
        for (Map.Entry<Node, Double> entry : entries) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private boolean hasSocialEdgeBetween(String nodeA, String nodeB) {
        for (Edge edge : getOutEdges(nodeA)) {
            if ("social".equals(edge.getEdgeType()) && edge.getTarget().getNodeId().equals(nodeB)) {
                return true;
            }
        }
        for (Edge edge : getInEdges(nodeA)) {
            if ("social".equals(edge.getEdgeType()) && edge.getSource().getNodeId().equals(nodeB)) {
                return true;
            }
        }
        return false;
    }

    // ============== 综合打分排序 ==============

    /**
     * 综合打分（默认使用全图PageRank）
     */
    public List<VideoScore> rankCandidatesByCompositeScore(String userNodeId, double alpha, double beta) {
        return rankCandidatesByCompositeScore(userNodeId, alpha, beta, "full");
    }

    /**
     * 综合打分：结合路径距离和PageRank热度对候选视频排序
     * @param prMode "full" 全图PageRank | "watch" 组员的watch-based PageRank (pagerank.py)
     */
    public List<VideoScore> rankCandidatesByCompositeScore(String userNodeId, double alpha, double beta, String prMode) {
        Map<String, Integer> videoDistanceMap = bfsVideoDistance(userNodeId, 3);
        if (videoDistanceMap.isEmpty()) {
            return Collections.emptyList();
        }

        // 选择PageRank算法
        Map<String, Double> pageRankScores;
        if ("watch".equals(prMode)) {
            pageRankScores = computeWatchBasedPageRank(0.85, 50, 1e-6);
        } else {
            pageRankScores = computePageRank(20, 0.85);
        }

        // 归一化
        double maxPR = 0.0;
        for (Double score : pageRankScores.values()) {
            if (score > maxPR) maxPR = score;
        }
        final double maxPageRank = maxPR > 0 ? maxPR : 1.0;

        // 计算综合分
        List<VideoScore> results = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : videoDistanceMap.entrySet()) {
            String videoId = entry.getKey();
            int distance = entry.getValue();
            double prScore = pageRankScores.getOrDefault(videoId, 0.0);
            double normalizedPR = prScore / maxPageRank;
            double finalScore = alpha * (1.0 / distance) + beta * normalizedPR;

            Node videoNode = getNode(videoId);
            if (videoNode != null) {
                results.add(new VideoScore(videoNode, distance, prScore, finalScore));
            }
        }

        results.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));
        return results;
    }

    /**
     * BFS遍历，返回视频节点ID到最短路径距离的映射
     */
    private Map<String, Integer> bfsVideoDistance(String userNodeId, int maxDepth) {
        Map<String, Integer> videoDistances = new LinkedHashMap<>();
        Node startNode = getNode(userNodeId);
        if (startNode == null || !startNode.isUser()) {
            return videoDistances;
        }

        Queue<BFSState> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        queue.offer(new BFSState(userNodeId, 0));
        visited.add(userNodeId);

        while (!queue.isEmpty()) {
            BFSState current = queue.poll();

            if (current.depth >= maxDepth) continue;

            for (Node neighbor : getUndirectedNeighbors(current.nodeId)) {
                String neighborId = neighbor.getNodeId();
                if (!visited.add(neighborId)) continue;

                int nextDepth = current.depth + 1;
                queue.offer(new BFSState(neighborId, nextDepth));

                if (neighbor.isVideo()) {
                    videoDistances.putIfAbsent(neighborId, nextDepth);
                }
            }
        }
        return videoDistances;
    }

    /**
     * 综合打分结果（内部类）
     */
    public static class VideoScore {
        public final Node video;
        public final int distance;
        public final double pageRankScore;
        public final double finalScore;

        VideoScore(Node video, int distance, double pageRankScore, double finalScore) {
            this.video = video;
            this.distance = distance;
            this.pageRankScore = pageRankScore;
            this.finalScore = finalScore;
        }
    }

    // ============== 图统计 ==============

    /**
     * 计算图密度
     * 密度 = 实际边数 / 最大可能边数
     */
    public double getDensity() {
        int n = nodeMap.size();
        if (n <= 1) return 0;
        double maxEdges = n * (n - 1.0);  // 有向图
        return edges.size() / maxEdges;
    }

    /**
     * 获取节点的出度（出边数）
     */
    public int getOutDegree(String nodeId) {
        return adjacencyList.getOrDefault(nodeId, new ArrayList<>()).size();
    }

    /**
     * 获取节点的入度（入边数）
     */
    public int getInDegree(String nodeId) {
        int count = 0;
        for (Edge edge : edges) {
            if (nodeId.equals(edge.getTarget().getNodeId())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 计算平均度
     */
    public double getAverageDegree() {
        if (nodeMap.isEmpty()) return 0;
        int totalDegree = 0;
        for (String nodeId : nodeMap.keySet()) {
            totalDegree += getOutDegree(nodeId);
        }
        return totalDegree / (double) nodeMap.size();
    }

    // ============== 打印和调试 ==============

    /**
     * 打印图的基本统计信息
     */
    public void printStatistics() {
        System.out.println("\n====== 图的统计信息 ======");
        System.out.println("节点总数: " + nodeMap.size());
        System.out.println("用户节点数: " + getUserNodes().size());
        System.out.println("视频节点数: " + getVideoNodes().size());
        System.out.println("边总数: " + edges.size());
        System.out.println("图密度: " + String.format("%.4f", getDensity()));
        System.out.println("平均度数: " + String.format("%.2f", getAverageDegree()));

        // 按边类型统计
        Map<String, Integer> edgeTypeCounts = new HashMap<>();
        for (Edge edge : edges) {
            edgeTypeCounts.put(edge.getEdgeType(),
                    edgeTypeCounts.getOrDefault(edge.getEdgeType(), 0) + 1);
        }
        System.out.println("\n边类型统计:");
        for (Map.Entry<String, Integer> entry : edgeTypeCounts.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }
    }

    /**
     * 打印某个节点的邻居
     */
    public void printNodeNeighbors(String nodeId) {
        Node node = getNode(nodeId);
        if (node == null) {
            System.out.println("节点不存在: " + nodeId);
            return;
        }

        System.out.println("\n节点 " + nodeId + " 的邻居:");
        System.out.println("  度数: " + node.getDegree());
        for (Node neighbor : node.getNeighbors()) {
            System.out.println("    -> " + neighbor.getNodeId() + " (" + neighbor.getDisplayName() + ")");
        }
    }

    private List<Node> getUndirectedNeighbors(String nodeId) {
        LinkedHashMap<String, Node> neighbors = new LinkedHashMap<>();
        for (Edge edge : getOutEdges(nodeId)) {
            neighbors.put(edge.getTarget().getNodeId(), edge.getTarget());
        }
        for (Edge edge : getInEdges(nodeId)) {
            neighbors.put(edge.getSource().getNodeId(), edge.getSource());
        }
        return new ArrayList<>(neighbors.values());
    }

    private static class BFSState {
        private final String nodeId;
        private final int depth;

        private BFSState(String nodeId, int depth) {
            this.nodeId = nodeId;
            this.depth = depth;
        }
    }
}
