package net.turtton.starj.web

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Forwards client-side SPA routes to the bundled index.html so that deep links
 * and reloads resolve to the single-page app shell. API, docs, and static
 * assets are matched before this controller and are not affected.
 */
@Controller
class SpaForwardingController {

    @GetMapping("/login", "/register")
    fun forwardSpaRoutes(): String = "forward:/index.html"
}
