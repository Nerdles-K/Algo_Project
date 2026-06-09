package com.synchplay.service;

import com.synchplay.auth.AppUser;
import com.synchplay.auth.AppUserRepository;
import com.synchplay.domain.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds demo1/demo2/demo3 accounts on first startup so the live demo works without manual registration.
 * Runs after GraphService (Order 3) so the user-node list is already populated.
 */
@Component
public class DemoDataService {

    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);

    // 4th field = pinned graph node ("" → fall back to round-robin assignment).
    // demo2/demo3 are pinned to well-connected hub nodes in TWO DIFFERENT social
    // communities, so their Dijkstra-reachable neighborhoods (and recommendations)
    // are genuinely distinct — unlike the sparsely-connected default nodes.
    private static final String[][] DEMO_USERS = {
        {"demo1", "demo1@synchplay.dev", "ADMIN", ""},            // round-robin (unchanged)
        {"demo2", "demo2@synchplay.dev", "USER",  "user_11867"},  // hub of social community #0 (social=9, watch=9)
        {"demo3", "demo3@synchplay.dev", "USER",  "user_4295"},   // hub of social community #1 (social=7, watch=6)
    };
    private static final String DEMO_PASSWORD = "demo123";

    private final AppUserRepository repo;
    private final PasswordEncoder encoder;
    private final GraphService graphService;

    public DemoDataService(AppUserRepository repo, PasswordEncoder encoder, GraphService graphService) {
        this.repo = repo;
        this.encoder = encoder;
        this.graphService = graphService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(3)
    public void seedDemoUsers() {
        List<Node> userNodes = graphService.getGraph().getUserNodes();
        if (userNodes.isEmpty()) {
            log.warn("DemoDataService: no graph user nodes found, skipping demo seed");
            return;
        }
        userNodes.sort((a, b) -> a.getNodeId().compareTo(b.getNodeId()));

        for (String[] entry : DEMO_USERS) {
            String username = entry[0];
            String email    = entry[1];
            String role     = entry[2];
            String pinned   = entry[3];

            // Resolve the target graph node: pinned (if it exists as a user node) else round-robin.
            String graphNodeId;
            if (!pinned.isEmpty() && isUserNode(pinned)) {
                graphNodeId = pinned;
            } else {
                if (!pinned.isEmpty()) {
                    log.warn("Demo user '{}': pinned node {} not found in graph, falling back to round-robin", username, pinned);
                }
                int idx = (int) (repo.count() % userNodes.size());
                graphNodeId = userNodes.get(idx).getNodeId();
            }

            if (repo.existsByUsername(username)) {
                // Keep role AND graph node in sync for existing demo accounts (re-point if changed).
                final String targetNode = graphNodeId;
                repo.findByUsername(username).ifPresent(u -> {
                    boolean changed = false;
                    if (!role.equals(u.getRole())) {
                        u.setRole(role); changed = true;
                        log.info("Updated demo user '{}' role to {}", username, role);
                    }
                    if (!pinned.isEmpty() && !targetNode.equals(u.getGraphNodeId())) {
                        log.info("Re-pointed demo user '{}' graph node {} → {}", username, u.getGraphNodeId(), targetNode);
                        u.setGraphNodeId(targetNode); changed = true;
                    }
                    if (changed) repo.save(u);
                });
                continue;
            }

            repo.save(new AppUser(username, email, encoder.encode(DEMO_PASSWORD), graphNodeId, role));
            log.info("Seeded demo user '{}' (role={}) → graph node {}", username, role, graphNodeId);
        }
    }

    /** True if the given node id exists in the in-memory graph and is a user node. */
    private boolean isUserNode(String nodeId) {
        Node n = graphService.getGraph().getNode(nodeId);
        return n != null && n.isUser();
    }
}
