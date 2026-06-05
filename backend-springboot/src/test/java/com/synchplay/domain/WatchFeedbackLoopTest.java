package com.synchplay.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the watch-history feedback loop at the graph level (the core of
 * "watch more → recommendations shift"): {@code excludeWatched} drops videos
 * the user has a direct {@code watch} out-edge to, and a newly-added watch
 * edge (what {@code WatchHistoryController} writes on each click) immediately
 * removes that video from future recommendations.
 *
 * Fixture (uA's reachable videos: v1 watched directly, v2 & v3 via the graph):
 *   uA ──watch(0.1)──> v1
 *   uA ──social──────> uB ──watch──> v2
 *   uA ──social──────> uC ──watch──> v3
 *   v1 ──similar(0.5)─> v2
 */
class WatchFeedbackLoopTest {

    private Graph graph;
    private Node uA, v3;

    @BeforeEach
    void setUp() {
        graph = new Graph();
        uA = user("uA");
        Node uB = user("uB"), uC = user("uC");
        Node v1 = video("v1"), v2 = video("v2");
        v3 = video("v3");
        for (Node n : List.of(uA, uB, uC, v1, v2, v3)) graph.addNode(n);

        graph.addEdge(new Edge(uA, uB, "social", 1.0));
        graph.addEdge(new Edge(uA, uC, "social", 1.0));
        graph.addEdge(new Edge(uA, v1, "watch", 0.1));
        graph.addEdge(new Edge(uB, v2, "watch", 0.1));
        graph.addEdge(new Edge(uC, v3, "watch", 0.1));
        graph.addEdge(new Edge(v1, v2, "similar", 0.5));
    }

    private Node user(String id) { return new Node(id, "user", id.substring(1), "User " + id); }
    private Node video(String id) {
        Node n = new Node(id, "video", id.substring(1), "Video " + id);
        n.setAttribute("views", "1000");
        n.setAttribute("likes", "50");
        return n;
    }

    private Set<String> recommendedIds(boolean excludeWatched) {
        return graph.rankCandidatesByCompositeScore("uA", 0.5, 0.3, 0.2, "full", excludeWatched)
            .stream().map(vs -> vs.video.getNodeId()).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("excludeWatched=false keeps the user's already-watched video (legacy behavior)")
    void includesWatchedByDefault() {
        Set<String> ids = recommendedIds(false);
        assertTrue(ids.contains("v1"), "v1 is directly watched by uA but should appear when not excluding");
        assertTrue(ids.contains("v2") && ids.contains("v3"));
    }

    @Test
    @DisplayName("excludeWatched=true drops the user's directly-watched video")
    void excludesWatched() {
        Set<String> ids = recommendedIds(true);
        assertFalse(ids.contains("v1"), "v1 was watched by uA and must be excluded");
        assertTrue(ids.contains("v2"), "v2 not watched by uA -> still recommended");
        assertTrue(ids.contains("v3"), "v3 not watched by uA -> still recommended");
    }

    @Test
    @DisplayName("recording a new watch edge immediately removes that video from recommendations")
    void newWatchClosesLoop() {
        assertTrue(recommendedIds(true).contains("v3"), "precondition: v3 recommended before watching");

        // Simulate WatchHistoryController adding a watch edge on click
        graph.addEdge(new Edge(uA, v3, "watch", 0.1));

        assertFalse(recommendedIds(true).contains("v3"),
            "after watching v3, it must drop out of uA's recommendations");
    }
}
