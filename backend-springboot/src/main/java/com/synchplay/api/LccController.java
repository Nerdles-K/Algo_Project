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

    /** Returns the authenticated user's echo-chamber metrics (raw LCC + composite cocoon score). */
    @GetMapping
    public Map<String, Object> myLcc(@AuthenticationPrincipal AppUser user) {
        Graph g = graphService.getGraph();
        String nodeId = user.getGraphNodeId();
        double lcc = g.computeLocalClusteringCoefficient(nodeId);

        Map<String, Object> mine = new LinkedHashMap<>();
        mine.put("id", nodeId);
        mine.put("name", user.getUsername());
        mine.put("lcc", round4(lcc));
        mine.put("riskLevel", riskLevel(lcc));
        addCocoon(g, nodeId, mine);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mine", mine);
        result.put("isAdmin", user.isAdmin());
        return result;
    }

    /** Admin-only: returns echo-chamber metrics for ALL user nodes. */
    @GetMapping("/admin")
    public Map<String, Object> allLcc(@AuthenticationPrincipal AppUser user) {
        Graph g = graphService.getGraph();
        LinkedHashMap<Node, Double> lccMap = g.computeAllUserLCC();

        List<Map<String, Object>> items = lccMap.entrySet().stream()
            .map(e -> {
                String nodeId = e.getKey().getNodeId();
                double lcc = e.getValue();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", nodeId);
                m.put("name", e.getKey().getDisplayName());
                m.put("lcc", round4(lcc));
                m.put("riskLevel", riskLevel(lcc));
                addCocoon(g, nodeId, m);
                return m;
            })
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", items.size());
        result.put("users", items);
        return result;
    }

    /** Adds composite cocoon score, level, and the 3-axis breakdown (all 0-1, higher = more cocooned). */
    private static void addCocoon(Graph g, String nodeId, Map<String, Object> m) {
        m.put("cocoonScore", round4(g.computeCocoonScore(nodeId)));
        m.put("cocoonLevel", g.getCocoonLevel(nodeId));
        Map<String, Object> breakdown = new LinkedHashMap<>();
        g.computeCocoonBreakdown(nodeId).forEach((k, v) -> breakdown.put(k, round4(v)));
        m.put("breakdown", breakdown);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static String riskLevel(double lcc) {
        if (lcc >= 0.7) return "high";
        if (lcc >= 0.4) return "medium";
        return "low";
    }
}
