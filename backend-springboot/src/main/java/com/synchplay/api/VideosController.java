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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creator side: publish a video into the recommendation graph.
 *
 * "Upload" here does NOT host a file — SynchPlay is a recommendation engine over
 * external YouTube content. Publishing registers a new {@code video} node plus a
 * {@code creator→video} "uploaded" edge, so the video becomes reachable to the
 * creator's social circle and accrues PageRank like any other node. A real
 * YouTube id is required so the thumbnail/link work (otherwise the frontend's
 * dead-video filter would hide it).
 */
@RestController
@RequestMapping("/api/videos")
public class VideosController {

    /** Authorship edge weight — strong link from creator to their own video. */
    private static final double UPLOADED_EDGE_WEIGHT = 0.1;

    /** Matches a YouTube id in watch / youtu.be / embed / shorts URLs, or a bare 11-char id. */
    private static final Pattern YT_ID = Pattern.compile(
        "(?:youtu\\.be/|v=|/embed/|/shorts/)([A-Za-z0-9_-]{11})|^([A-Za-z0-9_-]{11})$");

    private final JdbcTemplate jdbc;
    private final GraphService graphService;

    public VideosController(JdbcTemplate jdbc, GraphService graphService) {
        this.jdbc = jdbc;
        this.graphService = graphService;
    }

    /** Publish a new video node + creator edge. */
    @PostMapping
    public Map<String, Object> publish(
            @AuthenticationPrincipal AppUser user,
            @RequestBody Map<String, Object> body) {

        String rawUrl = str(body.get("youtubeUrl"));
        String title  = str(body.get("title"));
        String channel = str(body.get("channel"));
        long views = parseLong(body.get("views"));
        long likes = parseLong(body.get("likes"));

        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        String videoId = extractYouTubeId(rawUrl);
        if (videoId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "a valid YouTube link or 11-char video id is required");
        }
        if (channel == null || channel.isBlank()) {
            channel = user.getUsername();   // default channel = the creator's username
        }

        String nodeId = "video_" + videoId;
        Graph g = graphService.getGraph();
        Node creator = g.getNode(user.getGraphNodeId());
        if (creator == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "creator graph node not found");
        }
        if (g.getNode(nodeId) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "this video is already in the graph");
        }

        // 1) Persist the video node, then 2) the authorship edge (FK order: node before edge)
        jdbc.update(
            "INSERT INTO nodes(node_id, node_type, original_id, display_name, channel, views, likes) " +
            "VALUES (?,?,?,?,?,?,?)",
            nodeId, "video", videoId, title, channel, views, likes);
        jdbc.update("INSERT INTO edges (src, dst, edge_type, weight) VALUES (?,?,?,?)",
            user.getGraphNodeId(), nodeId, "uploaded", UPLOADED_EDGE_WEIGHT);

        // 3) Mirror into the in-memory graph
        Node video = new Node(nodeId, "video", videoId, title);
        video.setAttribute("channel", channel);
        video.setAttribute("views", String.valueOf(views));
        video.setAttribute("likes", String.valueOf(likes));
        g.addNode(video);
        g.addEdge(new Edge(creator, video, "uploaded", UPLOADED_EDGE_WEIGHT));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("video", videoView(video));
        return result;
    }

    /** List the videos the current user has published. */
    @GetMapping("/mine")
    public Map<String, Object> mine(@AuthenticationPrincipal AppUser user) {
        Graph g = graphService.getGraph();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Edge e : g.getOutEdges(user.getGraphNodeId())) {
            if ("uploaded".equals(e.getEdgeType()) && e.getTarget().isVideo()) {
                items.add(videoView(e.getTarget()));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", items.size());
        result.put("videos", items);
        return result;
    }

    private Map<String, Object> videoView(Node v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getNodeId());
        m.put("videoId", v.getOriginalId());
        m.put("title", v.getDisplayName());
        m.put("channel", v.getAttribute("channel"));
        m.put("views", v.getAttribute("views"));
        m.put("likes", v.getAttribute("likes"));
        return m;
    }

    /** Extract an 11-char YouTube id from a URL or accept a bare id; null if none found. */
    static String extractYouTubeId(String input) {
        if (input == null) return null;
        Matcher m = YT_ID.matcher(input.trim());
        if (m.find()) {
            return m.group(1) != null ? m.group(1) : m.group(2);
        }
        return null;
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static long parseLong(Object o) {
        if (o == null) return 0L;
        try { return Long.parseLong(o.toString().trim()); }
        catch (NumberFormatException e) { return 0L; }
    }
}
