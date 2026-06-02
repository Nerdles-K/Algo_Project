package com.synchplay.api;

import com.synchplay.auth.AppUser;
import com.synchplay.domain.Edge;
import com.synchplay.domain.Graph;
import com.synchplay.domain.Node;
import com.synchplay.service.GraphService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/friends")
public class FriendsController {

    private final GraphService graphService;
    private final JdbcTemplate jdbc;

    public FriendsController(GraphService graphService, JdbcTemplate jdbc) {
        this.graphService = graphService;
        this.jdbc = jdbc;
    }

    /** Returns existing friends + recommended friends for the authenticated user. */
    @GetMapping
    public Map<String, Object> friends(
            @AuthenticationPrincipal AppUser user,
            @RequestParam(defaultValue = "10") int top) {

        String graphNodeId = user.getGraphNodeId();
        Graph g = graphService.getGraph();
        Node me = g.getNode(graphNodeId);
        if (me == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "graph node not found");

        // Existing friends: social edges where I am the source
        Set<String> existingIds = new HashSet<>();
        List<Map<String, String>> existing = new ArrayList<>();
        for (Edge e : g.getOutEdges(graphNodeId)) {
            if ("social".equals(e.getEdgeType())) {
                Node friend = e.getTarget();
                existingIds.add(friend.getNodeId());
                Map<String, String> m = new LinkedHashMap<>();
                m.put("id", friend.getNodeId());
                m.put("name", friend.getDisplayName());
                existing.add(m);
            }
        }

        // Also include social edges where I am the target (bidirectional view)
        for (Edge e : g.getInEdges(graphNodeId)) {
            if ("social".equals(e.getEdgeType())) {
                Node friend = e.getSource();
                if (!existingIds.contains(friend.getNodeId())) {
                    existingIds.add(friend.getNodeId());
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("id", friend.getNodeId());
                    m.put("name", friend.getDisplayName());
                    existing.add(m);
                }
            }
        }

        // Recommendations: exclude existing friends
        List<Node> recommended = graphService.getFriendRecommendationService().recommend(graphNodeId);
        List<Map<String, String>> recItems = recommended.stream()
            .filter(n -> !existingIds.contains(n.getNodeId()))
            .limit(top)
            .map(n -> {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("id", n.getNodeId());
                m.put("name", n.getDisplayName());
                return m;
            })
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("graphNodeId", graphNodeId);
        result.put("existingCount", existing.size());
        result.put("existing", existing);
        result.put("recommendedCount", recItems.size());
        result.put("recommended", recItems);
        return result;
    }

    /** Add a friend (creates a social edge). */
    @PostMapping
    public Map<String, Object> addFriend(
            @AuthenticationPrincipal AppUser user,
            @RequestBody Map<String, String> body) {

        String targetNodeId = body.get("targetNodeId");
        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetNodeId is required");
        }

        String myId = user.getGraphNodeId();
        Graph g = graphService.getGraph();
        Node me = g.getNode(myId);
        Node target = g.getNode(targetNodeId);
        if (me == null || target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "graph node not found");
        }
        if (myId.equals(targetNodeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot friend yourself");
        }

        // Check if edge already exists
        Edge existing = g.findEdge(me, target, "social");
        if (existing != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "already_friends");
            result.put("message", "You are already friends with this user");
            return result;
        }

        // Persist to DB
        jdbc.update("INSERT INTO edges (src, dst, edge_type, weight) VALUES (?,?,?,?)",
            myId, targetNodeId, "social", 1.0);

        // Add to in-memory graph
        Edge edge = new Edge(me, target, "social", 1.0);
        g.addEdge(edge);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("message", "Friend added");
        result.put("friend", Map.of("id", targetNodeId, "name", target.getDisplayName()));
        return result;
    }

    /** Remove a friend (deletes the social edge). */
    @DeleteMapping
    public Map<String, Object> removeFriend(
            @AuthenticationPrincipal AppUser user,
            @RequestBody Map<String, String> body) {

        String targetNodeId = body.get("targetNodeId");
        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetNodeId is required");
        }

        String myId = user.getGraphNodeId();
        Graph g = graphService.getGraph();
        Node me = g.getNode(myId);
        Node target = g.getNode(targetNodeId);
        if (me == null || target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "graph node not found");
        }

        Edge edge = g.findEdge(me, target, "social");
        if (edge == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "not_friends");
            result.put("message", "You are not friends with this user");
            return result;
        }

        // Remove from DB
        jdbc.update("DELETE FROM edges WHERE src = ? AND dst = ? AND edge_type = ?",
            myId, targetNodeId, "social");

        // Remove from in-memory graph
        g.removeEdge(edge);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("message", "Friend removed");
        return result;
    }
}
