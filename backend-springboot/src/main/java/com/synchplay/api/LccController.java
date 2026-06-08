package com.synchplay.api;

import com.synchplay.auth.AppUser;
import com.synchplay.domain.Graph;
import com.synchplay.domain.Node;
import com.synchplay.service.FriendRecommendationService;
import com.synchplay.service.GraphService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/lcc")
public class LccController {

    private final GraphService graphService;
    private final FriendRecommendationService friendRecommendationService;

    public LccController(GraphService graphService, FriendRecommendationService friendRecommendationService) {
        this.graphService = graphService;
        this.friendRecommendationService = friendRecommendationService;
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

    @GetMapping("/cocoon/me")
    public Map<String, Object> myCocoon(
            @AuthenticationPrincipal AppUser user,
            @RequestParam(defaultValue = "true") boolean includeRecommend) {

        Graph g = graphService.getGraph();
        String userId = user.getGraphNodeId();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("msg", "success");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("userName", user.getUsername());

        double lcc = g.computeLocalClusteringCoefficient(userId);
        double entropy = g.computeWatchTopicEntropy(userId);
        double prConc = g.computePRConcentration(userId);
        double cocoonScore = g.computeCocoonScore(userId);
        String cocoonLevel = g.getCocoonLevel(userId);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("lcc", Math.round(lcc * 10000.0) / 10000.0);
        metrics.put("contentEntropy", Math.round(entropy * 10000.0) / 10000.0);
        metrics.put("prConcentration", Math.round(prConc * 10000.0) / 10000.0);
        metrics.put("cocoonScore", Math.round(cocoonScore * 10000.0) / 10000.0);
        metrics.put("cocoonLevel", cocoonLevel);
        metrics.put("riskLevel", riskLevel(lcc));
        data.put("metrics", metrics);

        if (includeRecommend) {
            Map<String, Object> rec = new LinkedHashMap<>();
            List<Map<String, Object>> videos = g.rankCandidatesByCompositeScore(userId, 10).stream()
                    .limit(5)
                    .map(v -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", v.getNodeId());
                        m.put("title", v.getDisplayName());
                        m.put("channel", v.getAttribute("channel"));
                        return m;
                    }).collect(Collectors.toList());
            rec.put("videos", videos);

            List<Map<String, Object>> friends = friendRecommendationService.recommendByCocoonLevel(userId).stream()
                    .limit(5)
                    .map(f -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", f.getNodeId());
                        m.put("name", f.getDisplayName());
                        return m;
                    }).collect(Collectors.toList());
            rec.put("friends", friends);
            data.put("recommend", rec);
        }

        result.put("data", data);
        return result;
    }

    @GetMapping("/cocoon/admin/stat")
    public Map<String, Object> cocoonStat(@AuthenticationPrincipal AppUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!user.isAdmin()) {
            result.put("code", 403);
            result.put("msg", "no permission");
            return result;
        }
        Graph g = graphService.getGraph();
        int mild = 0, moderate = 0, severe = 0;
        double totalScore = 0.0;
        int userCnt = 0;

        for (Node n : g.getNodes().values()) {
            if (n.isUser()) {
                userCnt++;
                String level = g.getCocoonLevel(n.getNodeId());
                double sc = g.computeCocoonScore(n.getNodeId());
                totalScore += sc;
                switch (level) {
                    case "mild" -> mild++;
                    case "moderate" -> moderate++;
                    case "severe" -> severe++;
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUser", userCnt);
        data.put("avgCocoonScore", userCnt > 0 ? Math.round((totalScore / userCnt) * 10000.0) / 10000.0 : 0.0);
        data.put("mild", mild);
        data.put("moderate", moderate);
        data.put("severe", severe);

        result.put("code", 200);
        result.put("msg", "success");
        result.put("data", data);
        return result;
    }

    private static String riskLevel(double lcc) {
        if (lcc >= 0.7) return "high";
        if (lcc >= 0.4) return "medium";
        return "low";
    }
}
