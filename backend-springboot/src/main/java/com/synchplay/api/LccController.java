package com.synchplay.api;

import com.synchplay.auth.AppUser;
import com.synchplay.domain.Graph;
import com.synchplay.domain.Node;
import com.synchplay.service.GraphService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lcc")
public class LccController {

    private final GraphService graphService;

    public LccController(GraphService graphService) {
        this.graphService = graphService;
    }

    /** Returns LCC only for the authenticated user (personal echo chamber view). */
    @GetMapping
    public Map<String, Object> myLcc(@AuthenticationPrincipal AppUser user) {
        Graph g = graphService.getGraph();
        double score = g.computeLocalClusteringCoefficient(user.getGraphNodeId());

        Map<String, Object> mine = new LinkedHashMap<>();
        mine.put("id", user.getGraphNodeId());
        mine.put("name", user.getUsername());
        mine.put("lcc", Math.round(score * 10000.0) / 10000.0);
        mine.put("riskLevel", riskLevel(score));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mine", mine);
        result.put("isAdmin", user.isAdmin());
        return result;
    }

    /** Admin-only: returns LCC scores for ALL user nodes. */
    @GetMapping("/admin")
    public Map<String, Object> allLcc(@AuthenticationPrincipal AppUser user) {
        Graph g = graphService.getGraph();
        LinkedHashMap<Node, Double> lccMap = g.computeAllUserLCC();

        List<Map<String, Object>> items = lccMap.entrySet().stream()
            .map(e -> {
                double score = e.getValue();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", e.getKey().getNodeId());
                m.put("name", e.getKey().getDisplayName());
                m.put("lcc", Math.round(score * 10000.0) / 10000.0);
                m.put("riskLevel", riskLevel(score));
                return m;
            })
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", items.size());
        result.put("users", items);
        return result;
    }

    private static String riskLevel(double lcc) {
        if (lcc >= 0.7) return "high";
        if (lcc >= 0.4) return "medium";
        return "low";
    }
}
