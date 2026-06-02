package com.synchplay.config;

import com.synchplay.auth.AppUser;
import com.synchplay.auth.AppUserRepository;
import com.synchplay.auth.JwtService;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Parses Authorization: Bearer <token> header and populates SecurityContext with AppUser principal. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserRepository userRepo;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository userRepo) {
        this.jwtService = jwtService;
        this.userRepo = userRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                String username = claims.getSubject();
                Optional<AppUser> userOpt = userRepo.findByUsername(username);
                if (userOpt.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
                    AppUser user = userOpt.get();
                    String role = claims.get("role", String.class);
                    if (role == null) role = "USER";
                    List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role));
                    var authToken = new UsernamePasswordAuthenticationToken(
                        user, null, authorities);
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (JwtException ignored) {
                // invalid token: leave context unauthenticated; downstream handler returns 401
            }
        }
        chain.doFilter(req, res);
    }
}
