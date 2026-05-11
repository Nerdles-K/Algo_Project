package com.synchplay.api;

import com.synchplay.domain.Node;
import com.synchplay.service.GraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UsersController {

    private final GraphService graphService;

    public UsersController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping
    public List<Map<String, String>> list() {
        List<Node> users = graphService.getGraph().getUserNodes();
        users.sort(Comparator.comparing(Node::getNodeId));
        return users.stream()
            .map(n -> Map.of("id", n.getNodeId(), "name", n.getDisplayName()))
            .toList();
    }
}
