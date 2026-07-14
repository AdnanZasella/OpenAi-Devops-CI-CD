package com.example.ailab;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Enkelt REST-API for att testa AI-integrationen manuellt, t.ex. med curl
 * eller Postman.
 *
 * Sakerhet (Steg 4/5, OWASP A01 - Broken Access Control):
 * Endpointen kraver nu en hemlig API-nyckel i headern "X-API-Key".
 * Utan ratt nyckel avvisas anropet med 401, INNAN nagot dyrt anrop
 * till OpenAI gors. Detta forhindrar att obehoriga kan spamma
 * endpointen och dranera var AI-budget.
 *
 * Exempel:
 * curl -X POST http://localhost:8080/api/ai/ask \
 *      -H "Content-Type: application/json" \
 *      -H "X-API-Key: din-hemliga-nyckel" \
 *      -d '{"message":"Vad ar Spring Boot?"}'
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiClientService aiClientService;

    // Steg 5 (A01-fix): var egen hemliga nyckel, laddas fran miljovariabel -
    // aldrig hardkodad, precis som OpenAI-nyckeln i AiClientService.
    @Value("${app.api.key}")
    private String expectedApiKey;

    public AiController(AiClientService aiClientService) {
        this.aiClientService = aiClientService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestHeader(value = "X-API-Key", required = false) String providedApiKey,
                           @Valid @RequestBody AskRequest request) {

        // Steg 5 (A01-fix): kontrollera nyckeln FORE vi gor nagot AI-anrop.
        if (providedApiKey == null || !providedApiKey.equals(expectedApiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ogiltig eller saknad API-nyckel.");
        }

        String answer = aiClientService.ask(request.message());
        return new AskResponse(answer);
    }

    public record AskRequest(
            @NotBlank
            // Steg 5 (A04-fix): satter en ovre grans sa att ingen kan
            // skicka en orimligt stor text i varje anrop.
            @Size(max = 2000, message = "Meddelandet far vara max 2000 tecken.")
            String message) {
    }

    public record AskResponse(String answer) {
    }
}