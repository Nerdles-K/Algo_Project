package com.synchplay.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Serves the bundled single-page app (Vue) for client-side routes.
 *
 * Vue Router uses history mode, so deep links like /app/recommend or /login are
 * not real files. Spring would return 404 for them. This forwards those SPA routes
 * to index.html, letting the frontend router take over. /api/** and /media/** are
 * handled by their own controllers/resource handlers and are NOT matched here.
 */
@Controller
public class SpaController {

    @RequestMapping({"/", "/login", "/register", "/app/**"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
