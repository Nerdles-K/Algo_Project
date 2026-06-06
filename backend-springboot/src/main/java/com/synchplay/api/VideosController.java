package com.synchplay.api;

import com.synchplay.auth.AppUser;
import com.synchplay.domain.Edge;
import com.synchplay.domain.Graph;
import com.synchplay.domain.Node;
import com.synchplay.service.GraphService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    private final Path uploadsDir;

    public VideosController(JdbcTemplate jdbc, GraphService graphService,
                            @Value("${synchplay.uploads.dir}") String uploadsDir) {
        this.jdbc = jdbc;
        this.graphService = graphService;
        this.uploadsDir = Paths.get(uploadsDir).toAbsolutePath().normalize();
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

    /** Publish a real uploaded video file (native), with an optional client-captured thumbnail. */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Map<String, Object> upload(
            @AuthenticationPrincipal AppUser user,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "thumb", required = false) MultipartFile thumb,
            @RequestParam("title") String title,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "views", defaultValue = "0") long views,
            @RequestParam(value = "likes", defaultValue = "0") long likes) {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "video file is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "file must be a video/*");
        }
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        if (channel == null || channel.isBlank()) channel = user.getUsername();

        Graph g = graphService.getGraph();
        Node creator = g.getNode(user.getGraphNodeId());
        if (creator == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "creator graph node not found");
        }

        String uuid = UUID.randomUUID().toString().replace("-", "");
        String ext = extension(file.getOriginalFilename(), contentType);
        String mediaFile = uuid + ext;
        String thumbFile = (thumb != null && !thumb.isEmpty()) ? uuid + ".jpg" : null;
        String nodeId = "video_native_" + uuid;

        // Save files to the uploads dir
        try {
            Files.createDirectories(uploadsDir);
            file.transferTo(uploadsDir.resolve(mediaFile));
            if (thumbFile != null) {
                try (var in = thumb.getInputStream()) {
                    Files.copy(in, uploadsDir.resolve(thumbFile), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to store upload");
        }

        // Persist node (source=native) then authorship edge
        jdbc.update(
            "INSERT INTO nodes(node_id, node_type, original_id, display_name, channel, views, likes, " +
            "source, media_path, thumb_path) VALUES (?,?,?,?,?,?,?,?,?,?)",
            nodeId, "video", uuid, title, channel, views, likes, "native", mediaFile, thumbFile);
        jdbc.update("INSERT INTO edges (src, dst, edge_type, weight) VALUES (?,?,?,?)",
            user.getGraphNodeId(), nodeId, "uploaded", UPLOADED_EDGE_WEIGHT);

        // Mirror into in-memory graph
        Node video = new Node(nodeId, "video", uuid, title);
        video.setAttribute("channel", channel);
        video.setAttribute("views", String.valueOf(views));
        video.setAttribute("likes", String.valueOf(likes));
        video.setAttribute("source", "native");
        video.setAttribute("mediaPath", mediaFile);
        if (thumbFile != null) video.setAttribute("thumbPath", thumbFile);
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
        enrichSource(m, v);
        return m;
    }

    /** Adds source + native media/thumb urls to a video JSON map. Shared with other controllers. */
    public static void enrichSource(Map<String, Object> m, Node v) {
        String source = v.getAttribute("source");
        m.put("source", source != null ? source : "youtube");
        if ("native".equals(source)) {
            String media = v.getAttribute("mediaPath");
            String thumb = v.getAttribute("thumbPath");
            m.put("streamUrl", media != null ? "/media/" + media : null);
            m.put("thumbUrl",  thumb != null ? "/media/" + thumb : null);
        }
    }

    /** File extension (with dot) from the original filename, falling back to the content type. */
    private static String extension(String filename, String contentType) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) {
                String ext = filename.substring(dot).toLowerCase();
                if (ext.matches("\\.[a-z0-9]{1,5}")) return ext;
            }
        }
        if (contentType != null && contentType.contains("/")) {
            String sub = contentType.substring(contentType.indexOf('/') + 1);
            if (sub.equals("quicktime")) return ".mov";
            if (sub.matches("[a-z0-9]{1,5}")) return "." + sub;
        }
        return ".mp4";
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
