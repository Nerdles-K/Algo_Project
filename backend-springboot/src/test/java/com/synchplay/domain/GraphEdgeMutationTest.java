package com.synchplay.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the in-memory graph edge mutations that back the Friends
 * feature (FR-C.8): adding/removing a social edge and the friendship check
 * used by {@code GET /api/friends/{id}/recommend}.
 *
 * These mirror what {@code FriendsController} does to the in-memory graph
 * after persisting to Postgres, without needing a live database.
 *
 * Fixture:
 *   uA, uB, uC users; v1 video
 *   uA ──social──> uB        (uA already follows uB)
 */
class GraphEdgeMutationTest {

    private Graph graph;
    private Node uA, uB, uC;

    @BeforeEach
    void setUp() {
        graph = new Graph();
        uA = user("uA");
        uB = user("uB");
        uC = user("uC");
        Node v1 = new Node("v1", "video", "1", "Video v1");
        for (Node n : List.of(uA, uB, uC, v1)) graph.addNode(n);
        graph.addEdge(new Edge(uA, uB, "social", 1.0));
    }

    private Node user(String id) {
        return new Node(id, "user", id.substring(1), "User " + id);
    }

    @Test
    @DisplayName("findEdge locates an existing social edge by type")
    void findEdgeFindsExisting() {
        Edge e = graph.findEdge(uA, uB, "social");
        assertNotNull(e);
        assertEquals(uA, e.getSource());
        assertEquals(uB, e.getTarget());
        assertEquals("social", e.getEdgeType());
    }

    @Test
    @DisplayName("findEdge returns null when no such edge / wrong type / wrong direction")
    void findEdgeMissing() {
        assertNull(graph.findEdge(uA, uC, "social"), "no edge uA->uC");
        assertNull(graph.findEdge(uA, uB, "watch"), "edge type mismatch");
        assertNull(graph.findEdge(uB, uA, "social"), "directed: uB->uA does not exist");
    }

    @Test
    @DisplayName("addEdge creates a new social edge visible via findEdge and out-edges")
    void addFriendEdge() {
        assertNull(graph.findEdge(uA, uC, "social"));

        graph.addEdge(new Edge(uA, uC, "social", 1.0));

        Edge created = graph.findEdge(uA, uC, "social");
        assertNotNull(created, "new edge should be findable");

        long socialOut = graph.getOutEdges("uA").stream()
            .filter(e -> "social".equals(e.getEdgeType()))
            .count();
        assertEquals(2, socialOut, "uA should now follow uB and uC");
    }

    @Test
    @DisplayName("removeEdge deletes the social edge (unfollow)")
    void removeFriendEdge() {
        Edge e = graph.findEdge(uA, uB, "social");
        assertNotNull(e);

        graph.removeEdge(e);

        assertNull(graph.findEdge(uA, uB, "social"), "edge gone after removeEdge");
        assertTrue(graph.getOutEdges("uA").stream()
            .noneMatch(x -> "social".equals(x.getEdgeType())),
            "uA has no social out-edges left");
    }

    @Test
    @DisplayName("in-edges expose the reverse (bidirectional friends view)")
    void inEdgesReflectFollowers() {
        assertTrue(graph.getInEdges("uB").stream()
            .anyMatch(x -> "social".equals(x.getEdgeType()) && x.getSource().equals(uA)),
            "uB should see uA as an incoming social edge");
    }
}
