package com.synchplay.api;

import com.synchplay.auth.AppUser;
import com.synchplay.domain.Node;
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
@RequestMapping("/api/friends")
public class FriendsController {

    private final GraphService graphService;

    public FriendsController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping
    public Map<String, Object> friends(
            @AuthenticationPrincipal AppUser user,
            @RequestParam(defaultValue = "10") int top) {

        String graphNodeId = user.getGraphNodeId();
        List<Node> recommended = graphService.getFriendRecommendationService().recommend(graphNodeId);

        List<Map<String, String>> items = recommended.stream()
            .limit(top)
            .map(n -> {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("id", n.getNodeId());
                m.put("name", n.getDisplayName());
                return m;
            })
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("graphNodeId", graphNodeId);
        result.put("count", items.size());
        result.put("friends", items);
        return result;
    }
}
