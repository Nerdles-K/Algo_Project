package com.synchplay.api;

import com.synchplay.auth.AppUser;
import com.synchplay.domain.Edge;
import com.synchplay.domain.Graph;
import com.synchplay.domain.Node;
import com.synchplay.service.GraphService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/watch-history")
public class WatchHistoryController {

    /** Weight for a user→video watch edge — strong signal (small Dijkstra distance). */
    private static final double WATCH_EDGE_WEIGHT = 0.1;

    private final JdbcTemplate jdbc;
    private final GraphService graphService;

    public WatchHistoryController(JdbcTemplate jdbc, GraphService graphService) {
        this.jdbc = jdbc;
        this.graphService = graphService;
    }

    @PostMapping
    public Map<String, Object> record(
            @AuthenticationPrincipal AppUser user,
            @RequestBody Map<String, Object> body) {
        String videoNodeId = (String) body.get("videoNodeId");
        String videoId = (String) body.get("videoId");
        String title = (String) body.get("title");
        String channel = (String) body.get("channel");

        jdbc.update(
            "INSERT INTO watch_history (user_id, video_node_id, video_id, title, channel) VALUES (?,?,?,?,?)",
            user.getId(), videoNodeId, videoId, title, channel);

        // Close the feedback loop: turn the watch into a real user→video graph edge so it
        // (a) gets excluded from future recommendations and (b) shifts Dijkstra distance /
        // watch-based PageRank. Mirrors how FriendsController persists a social edge.
        boolean edgeAdded = addWatchEdge(user.getGraphNodeId(), videoNodeId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("recorded", true);
        result.put("graphEdgeAdded", edgeAdded);
        return result;
    }

    /** Adds a user→video "watch" edge to the in-memory graph + edges table if not already present.
     *  Returns true when a new edge was created. */
    private boolean addWatchEdge(String userNodeId, String videoNodeId) {
        if (userNodeId == null || videoNodeId == null) return false;

        Graph g = graphService.getGraph();
        Node me = g.getNode(userNodeId);
        Node video = g.getNode(videoNodeId);
        if (me == null || video == null) return false;          // unknown node — log only
        if (g.findEdge(me, video, "watch") != null) return false; // already watched — idempotent

        jdbc.update("INSERT INTO edges (src, dst, edge_type, weight) VALUES (?,?,?,?)",
            userNodeId, videoNodeId, "watch", WATCH_EDGE_WEIGHT);
        g.addEdge(new Edge(me, video, "watch", WATCH_EDGE_WEIGHT));
        return true;
    }

    @GetMapping
    public Map<String, Object> history(
            @AuthenticationPrincipal AppUser user,
            @RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT wh.id, wh.video_node_id, wh.video_id, wh.title, wh.channel, wh.watched_at, " +
            "       n.source, n.media_path, n.thumb_path " +
            "FROM watch_history wh LEFT JOIN nodes n ON n.node_id = wh.video_node_id " +
            "WHERE wh.user_id = ? ORDER BY wh.watched_at DESC LIMIT ?",
            user.getId(), limit);

        // Surface native media/thumb as ready-to-use /media URLs
        for (Map<String, Object> m : items) {
            Object media = m.get("media_path");
            Object thumb = m.get("thumb_path");
            if (media != null) m.put("streamUrl", "/media/" + media);
            if (thumb != null) m.put("thumbUrl",  "/media/" + thumb);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getId());
        result.put("count", items.size());
        result.put("history", items);
        return result;
    }
}
