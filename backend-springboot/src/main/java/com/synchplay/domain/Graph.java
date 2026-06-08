package com.synchplay.domain;

import java.util.*;


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

    public void addNode(Node node) {
        nodeMap.put(node.getNodeId(), node);
        adjacencyList.putIfAbsent(node.getNodeId(), new ArrayList<>());
        reverseAdjacencyList.putIfAbsent(node.getNodeId(), new ArrayList<>());
    }

    public Node getNode(String nodeId) {
        return nodeMap.get(nodeId);
    }

    public Collection<Node> getAllNodes() {
        return nodeMap.values();
    }

    public List<Node> getUserNodes() {
        List<Node> users = new ArrayList<>();
        for (Node node : nodeMap.values()) {
            if (node.isUser()) {
                users.add(node);
            }
        }
        return users;
    }

    public List<Node> getVideoNodes() {
        List<Node> videos = new ArrayList<>();
        for (Node node : nodeMap.values()) {
            if (node.isVideo()) {
                videos.add(node);
            }
        }
        return videos;
    }

    public int getNodeCount() {
        return nodeMap.size();
    }

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

    /** Find a social edge between two nodes (direction matters: src→dst). */
    public Edge findEdge(Node src, Node dst, String edgeType) {
        for (Edge e : getOutEdges(src.getNodeId())) {
            if (e.getTarget().getNodeId().equals(dst.getNodeId()) && e.getEdgeType().equals(edgeType)) {
                return e;
            }
        }
        return null;
    }

    public void removeEdge(Edge edge) {
        edges.remove(edge);

        List<Edge> outList = adjacencyList.get(edge.getSource().getNodeId());
        if (outList != null) outList.remove(edge);

        List<Edge> inList = reverseAdjacencyList.get(edge.getTarget().getNodeId());
        if (inList != null) inList.remove(edge);

        edge.getSource().removeNeighbor(edge.getTarget());
    }

    public List<Edge> getOutEdges(String nodeId) {
        return adjacencyList.getOrDefault(nodeId, new ArrayList<>());
    }

    public List<Edge> getInEdges(String nodeId) {
        return reverseAdjacencyList.getOrDefault(nodeId, new ArrayList<>());
    }

    public List<Edge> getAllEdges() {
        return edges;
    }

    public int getEdgeCount() {
        return edges.size();
    }

    public List<Edge> getEdgesByType(String edgeType) {
        List<Edge> result = new ArrayList<>();
        for (Edge edge : edges) {
            if (edgeType.equals(edge.getEdgeType())) {
                result.add(edge);
            }
        }
        return result;
    }

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

    public LinkedHashMap<Node, Double> getVideoPageRankScores(int iterations, double dampingFactor) {
        Map<String, Double> rankMap = computePageRank(iterations, dampingFactor);
        return sortVideoRanks(rankMap);
    }

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

    public double computeWatchTopicEntropy(String userId) {
        Node user = getNode(userId);
        if (user == null || !user.isUser()) {
            return 0.0;
        }

        // 1. 获取用户观看过的所有视频（watch边）
        Map<String, Integer> topicCount = new HashMap<>();
        int totalWatched = 0;
        for (Edge edge : getOutEdges(userId)) {
            if ("watch".equals(edge.getEdgeType()) && edge.getTarget().isVideo()) {
                Node video = edge.getTarget();
                String topic = video.getAttribute("topic"); // 视频节点需有topic属性（如"体育"/"娱乐"）
                if (topic == null || topic.isEmpty()) {
                    topic = "unknown";
                }
                topicCount.put(topic, topicCount.getOrDefault(topic, 0) + 1);
                totalWatched++;
            }
        }

        if (totalWatched == 0) {
            return 0.0; // 无观看记录，熵值为0
        }

        // 2. 计算信息熵 H = -Σ(p_i * log2(p_i))
        double entropy = 0.0;
        for (int count : topicCount.values()) {
            double p = (double) count / totalWatched;
            entropy -= p * (Math.log(p) / Math.log(2)); // 以2为底的对数
        }

        // 3. 归一化到 [0,1]（熵的最大值为log2(主题数)）
        int topicTypeCount = topicCount.size();
        if (topicTypeCount <= 1) {
            return 0.0;
        }
        double maxEntropy = Math.log(topicTypeCount) / Math.log(2);
        return entropy / maxEntropy;
    }

    public double computePRConcentration(String userId) {
        Node user = getNode(userId);
        if (user == null || !user.isUser()) {
            return 0.0;
        }

        // 1. 获取用户的候选推荐视频（BFS 2-hop）
        List<Node> candidateVideos = findCandidateVideosByBFS(userId, 2);
        if (candidateVideos.isEmpty()) {
            return 0.0;
        }

        // 2. 获取候选视频的Watch-Based PageRank
        Map<String, Double> prScores = computeWatchBasedPageRank(0.85, 50, 1e-6);

        // 3. 计算PR分数的方差（方差越小 → 分数越集中 → 同质化越高）
        double sum = 0.0, sumSquare = 0.0;
        int validCount = 0;
        for (Node video : candidateVideos) {
            double pr = prScores.getOrDefault(video.getNodeId(), 0.0);
            sum += pr;
            sumSquare += pr * pr;
            validCount++;
        }

        if (validCount == 0) {
            return 0.0;
        }

        double mean = sum / validCount;
        double variance = (sumSquare / validCount) - (mean * mean);

        // 4. 归一化方差到 [0,1]，作为集中度分数
        double maxVariance = 0.1; // 经验值，可根据实际数据调整
        return Math.min(variance / maxVariance, 1.0);
    }

    public double computeCocoonScore(String userId) {
        // 1. 获取三个核心指标
        double lcc = computeLocalClusteringCoefficient(userId); // 社交封闭度（0-1）
        double topicEntropy = computeWatchTopicEntropy(userId); // 内容多样性（0-1）
        double prConcentration = computePRConcentration(userId); // 内容集中度（0-1）

        // 2. 加权融合（权重可根据业务调整）
        double socialWeight = 0.4;    // 社交封闭度权重
        double contentDiversityWeight = 0.3; // 内容多样性权重（取反，因为熵越高茧房越弱）
        double contentConcentrationWeight = 0.3; // 内容集中度权重

        double cocoonScore = (socialWeight * lcc) +
                             (contentDiversityWeight * (1 - topicEntropy)) +
                             (contentConcentrationWeight * prConcentration);

        // 3. 限制分数在0-1之间
        return Math.max(0.0, Math.min(1.0, cocoonScore));
    }

    public String getCocoonLevel(String userId) {
        double score = computeCocoonScore(userId);
        if (score >= 0.7) {
            return "high";
        } else if (score >= 0.4) {
            return "medium";
        } else {
            return "low";
        }
    }
    private static final double DEFAULT_MAX_DISTANCE = 3.0;

    public List<VideoScore> rankCandidatesByCompositeScore(String userNodeId, double alpha, double beta) {
        return rankCandidatesByCompositeScore(userNodeId, alpha, beta, 0.0, "full");
    }

    public List<VideoScore> rankCandidatesByCompositeScore(String userNodeId, double alpha, double beta, String prMode) {
        return rankCandidatesByCompositeScore(userNodeId, alpha, beta, 0.0, prMode);
    }

    public List<VideoScore> rankCandidatesByCompositeScore(
            String userNodeId, double alpha, double beta, double gamma, String prMode) {
        return rankCandidatesByCompositeScore(userNodeId, alpha, beta, gamma, prMode, false);
    }

    public List<VideoScore> rankCandidatesByCompositeScore(
            String userNodeId, double alpha, double beta, double gamma, String prMode, boolean excludeWatched) {

        Map<String, Double> videoDistanceMap = dijkstraVideoDistance(userNodeId, DEFAULT_MAX_DISTANCE);
        if (videoDistanceMap.isEmpty()) {
            return Collections.emptyList();
        }

        // 用户已直接观看过的视频（user→video "watch" 出边），可选剔除
        Set<String> watchedByUser = Collections.emptySet();
        if (excludeWatched) {
            watchedByUser = new HashSet<>();
            for (Edge e : getOutEdges(userNodeId)) {
                if ("watch".equals(e.getEdgeType())) {
                    watchedByUser.add(e.getTarget().getNodeId());
                }
            }
        }

        // PageRank
        Map<String, Double> pageRankScores = "watch".equals(prMode)
                ? computeWatchBasedPageRank(0.85, 50, 1e-6)
                : computePageRank(20, 0.85);

        double maxPR = pageRankScores.values().stream().max(Double::compare).orElse(1.0);
        if (maxPR <= 0) maxPR = 1.0;

        // Popularity：用 log 尺度归一化，避免极端 viral 视频垄断
        // 同时记录最小距离 → 用于把 distanceScore 也归一化到 0~1，
        // 与 normalizedPR / popularity 同量纲（否则 distanceScore 可达 ~100，量级失衡）。
        double maxLogViews = 0.0, maxLogLikes = 0.0;
        double minDistance = Double.POSITIVE_INFINITY;
        for (String vid : videoDistanceMap.keySet()) {
            Node v = getNode(vid);
            if (v == null) continue;
            maxLogViews = Math.max(maxLogViews, Math.log1p(parseLongAttr(v, "views")));
            maxLogLikes = Math.max(maxLogLikes, Math.log1p(parseLongAttr(v, "likes")));
            minDistance = Math.min(minDistance, videoDistanceMap.get(vid));
        }
        if (maxLogViews <= 0) maxLogViews = 1.0;
        if (maxLogLikes <= 0) maxLogLikes = 1.0;
        // 最近视频的 distanceScore 作为归一化分母（最近视频归一化后 = 1.0）
        double maxDistanceScore = 1.0 / Math.max(minDistance, 0.01);

        // 权重归一化（α+β+γ=1）
        double sum = alpha + beta + gamma;
        double aN = sum > 0 ? alpha / sum : 0.0;
        double bN = sum > 0 ? beta  / sum : 0.0;
        double gN = sum > 0 ? gamma / sum : 0.0;

        List<VideoScore> results = new ArrayList<>();
        for (Map.Entry<String, Double> entry : videoDistanceMap.entrySet()) {
            String videoId = entry.getKey();
            double distance = entry.getValue();
            Node videoNode = getNode(videoId);
            if (videoNode == null) continue;
            if (watchedByUser.contains(videoId)) continue;

            double prScore = pageRankScores.getOrDefault(videoId, 0.0);
            double normalizedPR = prScore / maxPR;

            double views = parseLongAttr(videoNode, "views");
            double likes = parseLongAttr(videoNode, "likes");
            double popularity = 0.6 * (Math.log1p(views) / maxLogViews)
                              + 0.4 * (Math.log1p(likes) / maxLogLikes);

            // 归一化到 0~1：最近的视频 = 1.0，越远越接近 0（= minDistance / distance）
            double distanceScore = (1.0 / Math.max(distance, 0.01)) / maxDistanceScore;
            double finalScore = aN * distanceScore + bN * normalizedPR + gN * popularity;

            results.add(new VideoScore(videoNode, distance, prScore, popularity, finalScore));
        }

        results.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));
        return results;
    }

    private static long parseLongAttr(Node n, String key) {
        String s = n.getAttribute(key);
        if (s == null || s.isEmpty()) return 0L;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0L; }
    }

    private Map<String, Double> dijkstraVideoDistance(String userNodeId, double maxDistance) {
        Map<String, Double> videoDistances = new LinkedHashMap<>();
        Node startNode = getNode(userNodeId);
        if (startNode == null || !startNode.isUser()) {
            return videoDistances;
        }

        Map<String, Double> dist = new HashMap<>();
        PriorityQueue<DijkstraEntry> pq = new PriorityQueue<>((a, b) -> Double.compare(a.distance, b.distance));

        dist.put(userNodeId, 0.0);
        pq.offer(new DijkstraEntry(userNodeId, 0.0));

        while (!pq.isEmpty()) {
            DijkstraEntry current = pq.poll();
            if (current.distance > dist.getOrDefault(current.nodeId, Double.POSITIVE_INFINITY)) continue;
            if (current.distance > maxDistance) continue;

            Node currentNode = getNode(current.nodeId);
            if (currentNode != null && currentNode.isVideo() && !current.nodeId.equals(userNodeId)) {
                videoDistances.putIfAbsent(current.nodeId, current.distance);
            }

            for (Edge edge : getOutEdges(current.nodeId)) {
                relax(edge.getTarget().getNodeId(), current.distance + edge.getWeight(), dist, pq, maxDistance);
            }
            for (Edge edge : getInEdges(current.nodeId)) {
                relax(edge.getSource().getNodeId(), current.distance + edge.getWeight(), dist, pq, maxDistance);
            }
        }
        return videoDistances;
    }

    private void relax(String neighborId, double newDist, Map<String, Double> dist,
                       PriorityQueue<DijkstraEntry> pq, double maxDistance) {
        if (newDist > maxDistance) return;
        if (newDist < dist.getOrDefault(neighborId, Double.POSITIVE_INFINITY)) {
            dist.put(neighborId, newDist);
            pq.offer(new DijkstraEntry(neighborId, newDist));
        }
    }

    private static class DijkstraEntry {
        final String nodeId;
        final double distance;
        DijkstraEntry(String nodeId, double distance) {
            this.nodeId = nodeId;
            this.distance = distance;
        }
    }

    public static class VideoScore {
        public final Node video;
        public final double distance;          // Dijkstra 加权距离
        public final double pageRankScore;
        public final double popularityScore;   // log(views)+log(likes) 归一化
        public final double finalScore;

        VideoScore(Node video, double distance, double pageRankScore, double popularityScore, double finalScore) {
            this.video = video;
            this.distance = distance;
            this.pageRankScore = pageRankScore;
            this.popularityScore = popularityScore;
            this.finalScore = finalScore;
        }
    }

    public double getDensity() {
        int n = nodeMap.size();
        if (n <= 1) return 0;
        double maxEdges = n * (n - 1.0);  // 有向图
        return edges.size() / maxEdges;
    }

    public int getOutDegree(String nodeId) {
        return adjacencyList.getOrDefault(nodeId, new ArrayList<>()).size();
    }

    public int getInDegree(String nodeId) {
        int count = 0;
        for (Edge edge : edges) {
            if (nodeId.equals(edge.getTarget().getNodeId())) {
                count++;
            }
        }
        return count;
    }

    public double getAverageDegree() {
        if (nodeMap.isEmpty()) return 0;
        int totalDegree = 0;
        for (String nodeId : nodeMap.keySet()) {
            totalDegree += getOutDegree(nodeId);
        }
        return totalDegree / (double) nodeMap.size();
    }

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
