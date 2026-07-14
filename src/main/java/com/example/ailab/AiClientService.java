package com.example.ailab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Saker tjänst som agerar saker proxy mot OpenAI:s Chat Completions-API.
 *
 * Steg 1: API-nyckeln laddas fran miljovariabler, aldrig hardkodad.
 *         Fail-fast vid uppstart om nyckeln saknas.
 * Steg 2: Strikta timeouts sa att ett hangande AI-anrop inte later
 *         servertradar i evighet.
 * Steg 3: En strikt systemprompt + lag temperature for forutsagbara svar.
 */
@Service
public class AiClientService {

    private static final Logger log = LoggerFactory.getLogger(AiClientService.class);

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";

    // Steg 3: Systemprompt som styr AI:ns beteende, sprak och format.
    private static final String SYSTEM_PROMPT = """
            Du ar en hjalpsam assistent som svarar kort och koncist pa svenska.
            Svara endast med ren text - ingen markdown, inga kodblock, ingen konverserande inledning.
            Om fragan inte kan besvaras, svara exakt: "Jag kan inte hjalpa med det."
            """;

    @Value("${openai.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestClient restClient;

    /**
     * Steg 1 (fail-fast): Om nyckeln saknas ska applikationen krascha
     * direkt vid uppstart - inte forst nar nagon forsoker anropa AI:n.
     *
     * Steg 2: Vi konfigurerar en egen request factory med strikta
     * timeouts innan RestClient byggs.
     */
    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("CRITICAL: API key is missing.");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000); // max 2s for att etablera anslutning
        requestFactory.setReadTimeout(8000);    // max 8s for att vanta pa svar

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(OPENAI_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    /**
     * Skickar anvandarens text till AI:n tillsammans med systemprompten
     * och returnerar AI:ns textsvar.
     *
     * Vi fangar fel runt sjalva HTTP-anropet (t.ex. timeout) sa att
     * applikationen aldrig kraschar pa grund av ett misslyckat AI-anrop -
     * den returnerar istallet ett sakert standardmeddelande.
     */
    public String ask(String userInput) {
        Map<String, Object> payload = Map.of(
                "model", MODEL,
                "temperature", 0.1,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userInput)
                )
        );

        try {
            String rawResponse = restClient.post()
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            return extractMessageContent(rawResponse);
        } catch (Exception e) {
            // Fangar bl.a. timeouts (ResourceAccessException) och andra
            // natverksrelaterade fel sa att anropet inte kraschar appen.
            log.error("Anrop till AI-tjansten misslyckades", e);
            return "AI-tjansten ar tillfalligt otillganglig. Forsok igen senare.";
        }
    }

    /**
     * OpenAI:s svar kommer inlindat i ett storre JSON-objekt
     * (choices -> message -> content). Vi maste plocka ut den
     * faktiska textens innehall, och om svaret av nagon anledning
     * inte gar att tolka returnerar vi ett sakert fallback-meddelande
     * istallet for att lata ett undantag spridas vidare.
     */
    private String extractMessageContent(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("Kunde inte tolka AI-svaret: {}", rawResponse, e);
            return "AI-tjansten returnerade ett ovantat svar.";
        }
    }
}
