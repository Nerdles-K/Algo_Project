package com.synchplay.api;

import com.synchplay.domain.Graph;
import com.synchplay.domain.Node;
import com.synchplay.service.GraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pagerank")
public class PageRankController {

    private final GraphService graphService;

    public PageRankController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping
    public Map<String, Object> pagerank(
            @RequestParam(defaultValue = "full") String prMode,
            @RequestParam(defaultValue = "15") int top) {

        Graph g = graphService.getGraph();
        LinkedHashMap<Node, Double> videoScores = "watch".equals(prMode)
            ? g.getVideoWatchBasedPageRankScores(0.85, 50, 1e-6)
            : g.getVideoPageRankScores(20, 0.85);

        List<Map<String, Object>> items = videoScores.entrySet().stream()
            .limit(top)
            .map(e -> {
                Node n = e.getKey();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", n.getNodeId());
                m.put("title", n.getDisplayName());
                m.put("videoId", n.getOriginalId());
                m.put("channel", n.getAttribute("channel"));
                m.put("views", n.getAttribute("views"));
                m.put("likes", n.getAttribute("likes"));
                m.put("pageRankScore", e.getValue());
                VideosController.enrichSource(m, n);
                return m;
            })
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prMode", prMode);
        result.put("count", items.size());
        result.put("videos", items);
        return result;
    }
}
