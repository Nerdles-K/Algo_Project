package com.synchplay.api;

import com.synchplay.auth.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/watch-history")
public class WatchHistoryController {

    private final JdbcTemplate jdbc;

    public WatchHistoryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("recorded", true);
        return result;
    }

    @GetMapping
    public Map<String, Object> history(
            @AuthenticationPrincipal AppUser user,
            @RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT id, video_node_id, video_id, title, channel, watched_at " +
            "FROM watch_history WHERE user_id = ? ORDER BY watched_at DESC LIMIT ?",
            user.getId(), limit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getId());
        result.put("count", items.size());
        result.put("history", items);
        return result;
    }
}
