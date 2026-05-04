package com.synchplay.service;

import com.synchplay.model.Graph;
import com.synchplay.model.Node;
import com.synchplay.model.Edge;

import java.util.*;

/**
 * 基于视频观看记录的好友推荐
 * 严格使用已有图结构：Node / Edge / Graph
 */
public class FriendRecommendation {

    private final Graph graph;

    // 传入已构建好的图
    public FriendRecommendation(Graph graph) {
        this.graph = graph;
    }

    /**
     * 核心接口：给用户ID，返回推荐好友列表
     * 逻辑：看过相同视频 → 推荐为好友
     */
    public List<Node> recommend(String userId) {
        // 1. 获取该用户看过的所有视频
        List<Node> myVideos = getWatchedVideos(userId);
        if (myVideos.isEmpty()) return Collections.emptyList();

        // 2. 统计：其他用户与我共同观看的视频数
        Map<String, Integer> userScore = new HashMap<>();
        Set<String> visited = new HashSet<>();
        visited.add(userId); // 排除自己

        for (Node video : myVideos) {
            List<Node> viewers = getUsersWatchedVideo(video.getNodeId());
            for (Node user : viewers) {
                String uid = user.getNodeId();
                if (visited.contains(uid)) continue;
                visited.add(uid);
                userScore.put(uid, userScore.getOrDefault(uid, 0) + 1);
            }
        }

        // 3. 按共同视频数从高到低排序
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(userScore.entrySet());
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

    // ==================== 辅助工具（完全适配原有结构） ====================

    /**
     * 获取一个用户观看过的所有视频
     */
    private List<Node> getWatchedVideos(String userId) {
        List<Node> videos = new ArrayList<>();
        for (Edge e : graph.getOutEdges(userId)) {
            if ("watch".equals(e.getEdgeType()) && e.getTarget().isVideo()) {
                videos.add(e.getTarget());
            }
        }
        return videos;
    }

    /**
     * 获取所有看过某视频的用户
     */
    private List<Node> getUsersWatchedVideo(String videoId) {
        List<Node> users = new ArrayList<>();
        for (Edge e : graph.getInEdges(videoId)) {
            if ("watch".equals(e.getEdgeType()) && e.getSource().isUser()) {
                users.add(e.getSource());
            }
        }
        return users;
    }
}
