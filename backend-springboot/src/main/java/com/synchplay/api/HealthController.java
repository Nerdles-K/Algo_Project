package com.synchplay.api;

import com.synchplay.service.GraphService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final GraphService graphService;
    private final JdbcTemplate jdbc;
    private final Instant startTime = Instant.now();

    public HealthController(GraphService graphService, JdbcTemplate jdbc) {
        this.graphService = graphService;
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> health() {
        String dbStatus;
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            dbStatus = "ok";
        } catch (Exception e) {
            dbStatus = "error";
        }
        var g = graphService.getGraph();
        return Map.of(
            "status", "ok",
            "uptimeSeconds", Duration.between(startTime, Instant.now()).toSeconds(),
            "db", dbStatus,
            "nodes", g.getNodeCount(),
            "edges", g.getEdgeCount(),
            "users", g.getUserNodes().size(),
            "videos", g.getVideoNodes().size()
        );
    }
}
