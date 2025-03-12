package com.example.config;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() throws Exception {
        // Create a TrustStrategy that trusts all certificates
        TrustStrategy trustStrategy = (chain, authType) -> true;

        // Build an SSLContext with the custom TrustStrategy
        SSLContext sslContext = SSLContextBuilder.create()
                .loadTrustMaterial(null, trustStrategy)
                .build();

        // Create a connection manager using the SSLContext
        PoolingHttpClientConnectionManager connectionManager =
                new PoolingHttpClientConnectionManager();
        
        // Build the CloseableHttpClient using the connection manager
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setSSLContext(sslContext) // Correct method in HttpClient 5
                .setSSLHostnameVerifier(new DefaultHostnameVerifier()) // Avoid deprecated methods
                .build();

        // Create a request factory using the custom HttpClient
        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(requestFactory);
    }
}
