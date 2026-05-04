package com.synchplay;

import com.synchplay.model.Graph;
import com.synchplay.model.Node;
import com.synchplay.model.Edge;
import com.synchplay.service.FriendRecommendation;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Graph graph = new Graph();
        FriendRecommendation fr = new FriendRecommendation(graph);

        List<Node> friends = fr.recommend("user_1");

        System.out.println("=== Friend recommendation ===");
        for (Node user : friends) {
            System.out.println("recommended users：" + user.getNodeId() + " | " + user.getDisplayName());
        }
    }
}
