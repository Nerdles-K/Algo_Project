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
@Order(3)
public class DemoDataService {

    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);

    private static final String[][] DEMO_USERS = {
        {"demo1", "demo1@synchplay.dev"},
        {"demo2", "demo2@synchplay.dev"},
        {"demo3", "demo3@synchplay.dev"},
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
            if (repo.existsByUsername(username)) {
                log.debug("Demo user '{}' already exists, skipping", username);
                continue;
            }
            int idx = (int) (repo.count() % userNodes.size());
            String graphNodeId = userNodes.get(idx).getNodeId();
            repo.save(new AppUser(username, email, encoder.encode(DEMO_PASSWORD), graphNodeId));
            log.info("Seeded demo user '{}' → graph node {}", username, graphNodeId);
        }
    }
}
