package com.brightline.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.brightline.monitor.config.IncidentSummaryProperties;
import com.brightline.monitor.domain.HealthStatus;
import com.brightline.monitor.service.IncidentMetrics;
import com.brightline.monitor.service.IncidentSummaryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class IncidentSummaryServiceTest {
    private static final String ENDPOINT = "https://gemini.test/v1beta/models/{model}:generateContent";
    private static final String EXPECTED_URL =
            "https://gemini.test/v1beta/models/gemini-2.5-flash:generateContent";
    private static final String LLM_SUMMARY =
            "Greenhouse ATS Sync is failing half its requests, mostly due to timeouts.";
    private static final IncidentMetrics METRICS = new IncidentMetrics(
            "Greenhouse ATS Sync", HealthStatus.RED, 60, 10, 5, 1, 4, 0, 0, 0);

    @Test
    void usesGeminiSummaryAndCachesItForUnchangedMetrics() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        IncidentSummaryProperties properties = properties("test-key");
        IncidentSummaryService service = new IncidentSummaryService(builder.build(), properties, new ObjectMapper());
        server.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Greenhouse ATS Sync")))
                .andRespond(withSuccess(response(LLM_SUMMARY), MediaType.APPLICATION_OCTET_STREAM));

        assertThat(service.summarize(METRICS)).isEqualTo(LLM_SUMMARY);
        assertThat(service.summarize(METRICS)).isEqualTo(LLM_SUMMARY);
        server.verify();
    }

    @Test
    void fallsBackToADataBasedSentenceWhenGeminiFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        IncidentSummaryProperties properties = properties("test-key");
        IncidentSummaryService service = new IncidentSummaryService(builder.build(), properties, new ObjectMapper());
        server.expect(requestTo(EXPECTED_URL)).andRespond(withServerError());

        assertThat(service.summarize(METRICS))
                .isEqualTo("Greenhouse ATS Sync had 5 of 10 requests fail (50%) in the last 60 minutes, mostly due to timeouts.");
        server.verify();
    }

    @Test
    void usesMetricsFallbackWhenApiKeyIsMissing() {
        IncidentSummaryProperties properties = properties("");
        IncidentSummaryService service = new IncidentSummaryService(
                RestClient.builder().build(), properties, new ObjectMapper());

        assertThat(service.summarize(METRICS))
                .isEqualTo("Greenhouse ATS Sync had 5 of 10 requests fail (50%) in the last 60 minutes, mostly due to timeouts.");
    }

    private String response(String summary) {
        return """
                {"candidates":[{"content":{"parts":[{"text":"%s"}]}}]}
                """.formatted(summary);
    }

    private IncidentSummaryProperties properties(String apiKey) {
        return new IncidentSummaryProperties(apiKey, ENDPOINT, "gemini-2.5-flash", 1000, 1000, 300);
    }
}
