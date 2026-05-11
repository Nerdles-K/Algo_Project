package com.synchplay.auth;

import com.synchplay.auth.dto.AuthResponse;
import com.synchplay.auth.dto.LoginRequest;
import com.synchplay.auth.dto.RegisterRequest;
import com.synchplay.domain.Node;
import com.synchplay.service.GraphService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository repo;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final GraphService graphService;
    private final AtomicInteger graphUserCursor = new AtomicInteger(0);

    public AuthController(AppUserRepository repo, PasswordEncoder encoder, JwtService jwt, GraphService graphService) {
        this.repo = repo;
        this.encoder = encoder;
        this.jwt = jwt;
        this.graphService = graphService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        if (repo.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username already taken");
        }
        if (repo.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        String graphNodeId = pickGraphUserNode();
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
        return new AuthResponse.UserView(user.getId(), user.getUsername(), user.getEmail(), user.getGraphNodeId());
    }

    private AuthResponse toResponse(AppUser user) {
        return new AuthResponse(
            jwt.issueToken(user),
            new AuthResponse.UserView(user.getId(), user.getUsername(), user.getEmail(), user.getGraphNodeId()));
    }

    /**
     * Round-robin pick of a dataset user node so new accounts immediately have meaningful recs.
     * Deterministic order: sorted by nodeId for stable cursor across restarts.
     */
    private synchronized String pickGraphUserNode() {
        List<Node> users = graphService.getGraph().getUserNodes();
        if (users.isEmpty()) {
            throw new IllegalStateException("Graph has no user nodes — DataImportService likely did not run");
        }
        // Sort once per call is cheap (100 items)
        users.sort((a, b) -> a.getNodeId().compareTo(b.getNodeId()));
        int idx = (int) (repo.count() % users.size());
        graphUserCursor.set(idx);
        return users.get(idx).getNodeId();
    }
}
