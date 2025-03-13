package com.example.demo;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

	@Bean
	public RestTemplate restTemplate() throws Exception {

		SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(TrustAllStrategy.INSTANCE).build();

		// Create a TlsSocketStrategy from the SSLContext using the default strategy.
		TlsSocketStrategy tlsStrategy = new DefaultClientTlsStrategy(sslContext);

		// Build the connection manager with TLS configuration.
		HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
				.setTlsSocketStrategy(tlsStrategy).build();

		// Build the HttpClient with the connection manager.
		CloseableHttpClient httpClient = HttpClientBuilder.create()
				.setConnectionManager((PoolingHttpClientConnectionManager) connectionManager).evictExpiredConnections()
				.build();

		// Create the request factory and RestTemplate.
		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
		
		return new RestTemplate(requestFactory);
	}
}
