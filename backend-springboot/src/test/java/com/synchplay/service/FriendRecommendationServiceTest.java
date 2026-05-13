package com.synchplay.service;

import com.synchplay.domain.Edge;
import com.synchplay.domain.Graph;
import com.synchplay.domain.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FriendRecommendationServiceTest {

    private Graph graph;
    private FriendRecommendationService service;

    @BeforeEach
    void setUp() {
        graph = new Graph();
        // Users
        Node uA = user("uA"), uB = user("uB"), uC = user("uC"), uD = user("uD");
        // Videos
        Node v1 = video("v1"), v2 = video("v2"), v3 = video("v3");

        for (Node n : List.of(uA, uB, uC, uD, v1, v2, v3)) {
            graph.addNode(n);
        }

        // uA watched v1, v2
        graph.addEdge(new Edge(uA, v1, "watch", 1.0));
        graph.addEdge(new Edge(uA, v2, "watch", 1.0));

        // uB watched v1, v2, v3 (shares 2 videos with uA — top candidate)
        graph.addEdge(new Edge(uB, v1, "watch", 1.0));
        graph.addEdge(new Edge(uB, v2, "watch", 1.0));
        graph.addEdge(new Edge(uB, v3, "watch", 1.0));

        // uC watched v1 only (shares 1 with uA)
        graph.addEdge(new Edge(uC, v1, "watch", 1.0));

        // uD watched nothing (no overlap with uA)

        service = new FriendRecommendationService(graph);
    }

    private Node user(String id)  { return new Node(id, "user",  id.substring(1), id); }
    private Node video(String id) { return new Node(id, "video", id.substring(1), id); }

    @Test
    @DisplayName("Recommends users by descending count of shared watched videos")
    void recommendOrdersBySharedVideoCount() {
        List<Node> recs = service.recommend("uA");
        assertEquals(2, recs.size(), "uB and uC share videos with uA; uD does not");
        assertEquals("uB", recs.get(0).getNodeId(), "uB shares 2 videos → first");
        assertEquals("uC", recs.get(1).getNodeId(), "uC shares 1 video → second");
    }

    @Test
    @DisplayName("Excludes the user themselves from the recommendations")
    void excludesSelf() {
        List<Node> recs = service.recommend("uA");
        assertTrue(recs.stream().noneMatch(n -> n.getNodeId().equals("uA")));
    }

    @Test
    @DisplayName("Returns empty list for unknown user")
    void unknownUserReturnsEmpty() {
        assertTrue(service.recommend("ghost").isEmpty());
    }

    @Test
    @DisplayName("Returns empty list for user with no watch history")
    void noWatchHistoryReturnsEmpty() {
        assertTrue(service.recommend("uD").isEmpty());
    }

    @Test
    @DisplayName("Only user nodes appear in recommendations (never videos)")
    void onlyUserNodesReturned() {
        List<Node> recs = service.recommend("uA");
        for (Node n : recs) {
            assertTrue(n.isUser(), "recommendation list contained non-user node: " + n);
        }
    }
}
