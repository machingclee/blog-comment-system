package com.machingclee.blogcomment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Root API controller that redirects to the Swagger UI.
 */
@Tag(name = "API", description = "Root redirect to Swagger UI")
@RestController
@RequestMapping("/api")
public class ApiController {

    @Operation(summary = "Redirect to Swagger UI", description = "Redirects the root path to the Swagger UI page.")
    @GetMapping("")
    public RedirectView redirectToSwagger() {
        return new RedirectView("/swagger-ui/index.html");
    }
}
