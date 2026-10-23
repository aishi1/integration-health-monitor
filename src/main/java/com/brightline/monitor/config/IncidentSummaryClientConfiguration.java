package com.brightline.monitor.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class IncidentSummaryClientConfiguration {
    @Bean
    RestClient incidentSummaryRestClient(RestClient.Builder builder, IncidentSummaryProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis()));
        return builder.requestFactory(requestFactory).build();
    }
}
