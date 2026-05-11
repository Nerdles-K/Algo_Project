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
            @RequestParam(defaultValue = "0.6") double alpha,
            @RequestParam(defaultValue = "0.4") double beta,
            @RequestParam(defaultValue = "full") String prMode,
            @RequestParam(defaultValue = "20") int top) {

        String graphNodeId = user.getGraphNodeId();
        Graph g = graphService.getGraph();

        List<Graph.VideoScore> scores = g.rankCandidatesByCompositeScore(graphNodeId, alpha, beta, prMode);

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
                m.put("distance", vs.distance);
                m.put("pageRankScore", vs.pageRankScore);
                m.put("finalScore", vs.finalScore);
                return m;
            })
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("graphNodeId", graphNodeId);
        result.put("alpha", alpha);
        result.put("beta", beta);
        result.put("prMode", prMode);
        result.put("count", items.size());
        result.put("recommendations", items);
        return result;
    }
}
