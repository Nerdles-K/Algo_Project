package com.synchplay.auth;

import com.synchplay.auth.dto.AuthResponse;
import com.synchplay.auth.dto.LoginRequest;
import com.synchplay.auth.dto.RegisterRequest;
import com.synchplay.domain.Edge;
import com.synchplay.domain.Graph;
import com.synchplay.domain.Node;
import com.synchplay.service.GraphService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** New accounts follow this many of the most-connected existing users (light cold-start seed). */
    private static final int SEED_FOLLOW_COUNT = 3;
    private static final double SEED_EDGE_WEIGHT = 1.0;

    private final AppUserRepository repo;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final GraphService graphService;
    private final JdbcTemplate jdbc;

    public AuthController(AppUserRepository repo, PasswordEncoder encoder, JwtService jwt,
                          GraphService graphService, JdbcTemplate jdbc) {
        this.repo = repo;
        this.encoder = encoder;
        this.jwt = jwt;
        this.graphService = graphService;
        this.jdbc = jdbc;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        if (repo.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username already taken");
        }
        if (repo.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        String graphNodeId = createUserNode(req.username());
        AppUser user = new AppUser(
            req.username(),
            req.email(),
            encoder.encode(req.password()),
            graphNodeId);
        user = repo.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        AppUser user = repo.findByUsername(req.username())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }
        return toResponse(user);
    }

    @GetMapping("/me")
    public AuthResponse.UserView me(@AuthenticationPrincipal AppUser user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return new AuthResponse.UserView(user.getId(), user.getUsername(), user.getEmail(), user.getGraphNodeId(), user.getRole());
    }

    private AuthResponse toResponse(AppUser user) {
        return new AuthResponse(
            jwt.issueToken(user),
            new AuthResponse.UserView(user.getId(), user.getUsername(), user.getEmail(), user.getGraphNodeId(), user.getRole()));
    }

    /**
     * Creates a brand-new graph user node for the account (persisted + added to the in-memory graph),
     * then seeds a few "social" follow edges to the most-connected existing users so the new user has
     * meaningful friend/recommendation results from the first login. The account's own future
     * follows/watches attach to this node, so it diverges from the seed over time.
     *
     * @return the new node's id
     */
    private synchronized String createUserNode(String username) {
        Graph g = graphService.getGraph();
        if (g.getUserNodes().isEmpty()) {
            throw new IllegalStateException("Graph has no user nodes — DataImportService likely did not run");
        }

        // 1) Unique node id (retry on the astronomically unlikely collision)
        String nodeId;
        do {
            nodeId = "appuser_" + UUID.randomUUID().toString().substring(0, 8);
        } while (g.getNode(nodeId) != null);

        // 2) Persist node + add to in-memory graph
        jdbc.update(
            "INSERT INTO nodes(node_id, node_type, original_id, display_name) VALUES (?,?,?,?)",
            nodeId, "user", username, username);
        Node newNode = new Node(nodeId, "user", username, username);
        g.addNode(newNode);

        // 3) Light cold-start seed: follow the top-N most-connected existing users
        for (Node target : topUsersByDegree(g, nodeId, SEED_FOLLOW_COUNT)) {
            jdbc.update("INSERT INTO edges(src, dst, edge_type, weight) VALUES (?,?,?,?)",
                nodeId, target.getNodeId(), "social", SEED_EDGE_WEIGHT);
            g.addEdge(new Edge(newNode, target, "social", SEED_EDGE_WEIGHT));
        }
        return nodeId;
    }

    /** The k existing user nodes with the highest total degree, excluding {@code excludeId}. */
    private List<Node> topUsersByDegree(Graph g, String excludeId, int k) {
        return g.getUserNodes().stream()
            .filter(n -> !n.getNodeId().equals(excludeId))
            .sorted(Comparator.comparingInt(
                (Node n) -> g.getOutEdges(n.getNodeId()).size() + g.getInEdges(n.getNodeId()).size()).reversed())
            .limit(k)
            .toList();
    }
}
