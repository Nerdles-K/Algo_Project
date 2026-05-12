package com.synchplay.api;

import com.synchplay.auth.AppUser;
import com.synchplay.domain.Graph;
import com.synchplay.service.GraphService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    private final GraphService graphService;

    public RecommendController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping
    public Map<String, Object> recommend(
            @AuthenticationPrincipal AppUser user,
            @RequestParam(defaultValue = "0.5") double alpha,
            @RequestParam(defaultValue = "0.3") double beta,
            @RequestParam(defaultValue = "0.2") double gamma,
            @RequestParam(defaultValue = "full") String prMode,
            @RequestParam(defaultValue = "20") int top) {

        String graphNodeId = user.getGraphNodeId();
        Graph g = graphService.getGraph();

        List<Graph.VideoScore> scores = g.rankCandidatesByCompositeScore(graphNodeId, alpha, beta, gamma, prMode);

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
                return m;
            })
            .toList();

        // 归一化后返回，方便前端展示
        double sum = alpha + beta + gamma;
        double aN = sum > 0 ? alpha / sum : 0.0;
        double bN = sum > 0 ? beta  / sum : 0.0;
        double gN = sum > 0 ? gamma / sum : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("graphNodeId", graphNodeId);
        result.put("alpha", Math.round(aN * 100.0) / 100.0);
        result.put("beta",  Math.round(bN * 100.0) / 100.0);
        result.put("gamma", Math.round(gN * 100.0) / 100.0);
        result.put("prMode", prMode);
        result.put("count", items.size());
        result.put("recommendations", items);
        return result;
    }
}
