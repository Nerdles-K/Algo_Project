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

        Set<String> myFriendIds = socialFriendIds(g, graphNodeId);

        // Existing friends: social edges where I am the source
        Set<String> existingIds = new HashSet<>();
        List<Map<String, Object>> existing = new ArrayList<>();
        for (Edge e : g.getOutEdges(graphNodeId)) {
            if ("social".equals(e.getEdgeType())) {
                Node friend = e.getTarget();
                existingIds.add(friend.getNodeId());
                existing.add(friendEntry(g, graphNodeId, myFriendIds, friend));
            }
        }

        // Also include social edges where I am the target (bidirectional view)
        for (Edge e : g.getInEdges(graphNodeId)) {
            if ("social".equals(e.getEdgeType())) {
                Node friend = e.getSource();
                if (!existingIds.contains(friend.getNodeId())) {
                    existingIds.add(friend.getNodeId());
                    existing.add(friendEntry(g, graphNodeId, myFriendIds, friend));
                }
            }
        }

        // Recommendations: exclude existing friends
        List<Node> recommended = graphService.getFriendRecommendationService().recommend(graphNodeId);
        List<Map<String, Object>> recItems = recommended.stream()
            .filter(n -> !existingIds.contains(n.getNodeId()))
            .limit(top)
            .map(n -> friendEntry(g, graphNodeId, myFriendIds, n))
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

    /** Get recommendations for a specific friend (must already be following). */
    @GetMapping("/{friendNodeId}/recommend")
    public Map<String, Object> friendRecommend(
            @AuthenticationPrincipal AppUser user,
            @PathVariable String friendNodeId,
            @RequestParam(defaultValue = "0.5") double alpha,
            @RequestParam(defaultValue = "0.3") double beta,
            @RequestParam(defaultValue = "0.2") double gamma,
            @RequestParam(defaultValue = "full") String prMode,
            @RequestParam(defaultValue = "10") int top) {

        Graph g = graphService.getGraph();
        Node me = g.getNode(user.getGraphNodeId());
        Node friend = g.getNode(friendNodeId);
        if (friend == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "friend graph node not found");
        }

        // Verify friendship: must have a social edge from me to friend
        Edge socialEdge = g.findEdge(me, friend, "social");
        if (socialEdge == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "you must follow this user first");
        }

        // excludeWatched=true: recommend videos the friend has not already watched
        List<Graph.VideoScore> scores = g.rankCandidatesByCompositeScore(friendNodeId, alpha, beta, gamma, prMode, true);

        List<Map<String, Object>> items = scores.stream()
            .limit(top)
            .map(vs -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", vs.video.getNodeId());
                m.put("title", vs.video.getDisplayName());
                m.put("videoId", vs.video.getOriginalId());
                m.put("channel", vs.video.getAttribute("channel"));
                m.put("views", vs.video.getAttribute("views"));
                m.put("likes", vs.video.getAttribute("likes"));
                m.put("distance", Math.round(vs.distance * 1000.0) / 1000.0);
                m.put("pageRankScore", vs.pageRankScore);
                m.put("popularityScore", Math.round(vs.popularityScore * 10000.0) / 10000.0);
                m.put("finalScore", vs.finalScore);
                VideosController.enrichSource(m, vs.video);
                return m;
            })
            .toList();

        double sum = alpha + beta + gamma;
        double aN = sum > 0 ? alpha / sum : 0.0;
        double bN = sum > 0 ? beta  / sum : 0.0;
        double gN = sum > 0 ? gamma / sum : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("friendNodeId", friendNodeId);
        result.put("friendName", friend.getDisplayName());
        result.put("alpha", Math.round(aN * 100.0) / 100.0);
        result.put("beta",  Math.round(bN * 100.0) / 100.0);
        result.put("gamma", Math.round(gN * 100.0) / 100.0);
        result.put("prMode", prMode);
        result.put("count", items.size());
        result.put("recommendations", items);
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

    private static Set<String> socialFriendIds(Graph g, String nodeId) {
        Set<String> ids = new HashSet<>();
        for (Edge e : g.getOutEdges(nodeId)) {
            if ("social".equals(e.getEdgeType())) {
                ids.add(e.getTarget().getNodeId());
            }
        }
        for (Edge e : g.getInEdges(nodeId)) {
            if ("social".equals(e.getEdgeType())) {
                ids.add(e.getSource().getNodeId());
            }
        }
        return ids;
    }

    private static int countMutualFriends(Graph g, Set<String> myFriendIds, String myId, String theirId) {
        Set<String> theirFriends = socialFriendIds(g, theirId);
        theirFriends.remove(myId);
        theirFriends.retainAll(myFriendIds);
        return theirFriends.size();
    }

    private static Map<String, Object> friendEntry(Graph g, String myId, Set<String> myFriendIds, Node node) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", node.getNodeId());
        m.put("name", node.getDisplayName());
        m.put("mutualFriends", countMutualFriends(g, myFriendIds, myId, node.getNodeId()));
        return m;
    }
}
