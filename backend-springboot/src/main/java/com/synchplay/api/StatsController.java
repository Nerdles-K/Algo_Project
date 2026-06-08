package com.synchplay.api;

import com.synchplay.auth.AppUser;
import com.synchplay.domain.Graph;
import com.synchplay.service.GraphService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final GraphService graphService;

    public StatsController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping
    public Map<String, Object> stats(@AuthenticationPrincipal AppUser user) {
        if (user == null || !user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can access overview statistics");
        }
        Graph g = graphService.getGraph();
        int n = g.getNodeCount();
        int e = g.getEdgeCount();
        int u = g.getUserNodes().size();
        int v = g.getVideoNodes().size();
        double density = n <= 1 ? 0.0 : (double) e / ((long) n * (n - 1));
        double avgDeg  = n == 0 ? 0.0 : (double) e / n;   // matches v1 (out-edges per node)

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("totalNodes", n);
        graph.put("userNodes", u);
        graph.put("videoNodes", v);
        graph.put("totalEdges", e);
        graph.put("density", round(density, 4));
        graph.put("avgDegree", round(avgDeg, 2));

        Map<String, Integer> edgeTypes = new LinkedHashMap<>();
        edgeTypes.put("social",  g.getEdgesByType("social").size());
        edgeTypes.put("watch",   g.getEdgesByType("watch").size());
        edgeTypes.put("similar", g.getEdgesByType("similar").size());

        return Map.of("graph", graph, "edgeTypes", edgeTypes);
    }

    private static double round(double x, int places) {
        double scale = Math.pow(10, places);
        return Math.round(x * scale) / scale;
    }
}
