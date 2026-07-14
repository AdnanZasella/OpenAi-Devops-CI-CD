package com.example.ailab;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enkelt REST-API for att testa AI-integrationen manuellt, t.ex. med curl
 * eller Postman.
 *
 * Exempel:
 * curl -X POST http://localhost:8080/api/ai/ask \
 *      -H "Content-Type: application/json" \
 *      -d '{"message":"Vad ar Spring Boot?"}'
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiClientService aiClientService;

    public AiController(AiClientService aiClientService) {
        this.aiClientService = aiClientService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        String answer = aiClientService.ask(request.message());
        return new AskResponse(answer);
    }

    public record AskRequest(@NotBlank String message) {
    }

    public record AskResponse(String answer) {
    }
}
