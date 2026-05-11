package com.synchplay.service;

import com.synchplay.domain.Graph;
import com.synchplay.domain.Node;
import com.synchplay.domain.Edge;

import java.util.*;

/**
 * 好友推荐服务（组员实现）
 * 基于共同观看视频数推荐潜在好友
 * 算法：找出与目标用户看过相同视频的其他用户，按共同视频数降序排列
 */
public class FriendRecommendationService {

    private final Graph graph;

    public FriendRecommendationService(Graph graph) {
        this.graph = graph;
    }

    /**
     * 为核心用户推荐潜在好友
     * @param userId 目标用户ID
     * @return 推荐用户列表（按共同观看视频数降序，排除自己）
     */
    public List<Node> recommend(String userId) {
        if (graph.getNode(userId) == null) return Collections.emptyList();

        // 1. 获取该用户看过的所有视频及其观众
        Map<String, Integer> commonVideoCount = new HashMap<>();

        for (Edge edge : graph.getOutEdges(userId)) {
            if (!"watch".equals(edge.getEdgeType())) continue;
            String videoId = edge.getTarget().getNodeId();

            // 2. 找出也看过这个视频的所有其他用户
            for (Edge inEdge : graph.getInEdges(videoId)) {
                if (!"watch".equals(inEdge.getEdgeType())) continue;
                String otherUserId = inEdge.getSource().getNodeId();
                if (otherUserId.equals(userId)) continue; // 排除自己
                commonVideoCount.put(otherUserId,
                    commonVideoCount.getOrDefault(otherUserId, 0) + 1);
            }
        }

        if (commonVideoCount.isEmpty()) return Collections.emptyList();

        // 3. 按共同视频数降序排列
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(commonVideoCount.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        // 4. 转为Node列表返回
        List<Node> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sorted) {
            Node u = graph.getNode(entry.getKey());
            if (u != null && u.isUser()) {
                result.add(u);
            }
        }
        return result;
    }
}
