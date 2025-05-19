package com.example.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}


@Bean
public RouteLocator customRoutes(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("api_with_cb", r -> r.path("/api/**")
            .filters(f -> f.circuitBreaker(c -> c
                .setName("myCB")
                .setFallbackUri("forward:/fallback")
            ))

    
            .uri("lb://MY-SERVICE"))
        .build();
}

@Bean
public RouteLocator customRoutes(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("my-service", r -> r
            .path("/myservice/**")
            .filters(f -> f.circuitBreaker(c -> c
                .setName("myCircuitBreaker")
                .setFallbackUri("forward:/custom-fallback")))
            .uri("lb://MY-SERVICE"))
        .build();
}

@RestController
public class GatewayFallbackController {

    @RequestMapping("/custom-fallback")
    public Mono<ResponseEntity<String>> fallback(ServerWebExchange exchange) {
        Throwable cause = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);

        HttpStatus status;
        if (cause != null && cause.getClass().getSimpleName().equals("CallNotPermittedException")) {
            // Resilience4j throws this when circuit is open
            status = HttpStatus.SERVICE_UNAVAILABLE;
        } else if (cause != null && cause.getClass().getSimpleName().contains("Timeout")) {
            // Could be TimeoutException from Resilience4j
            status = HttpStatus.GATEWAY_TIMEOUT;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return Mono.just(ResponseEntity.status(status).body("Fallback triggered: " + status));
    }
}



@Configuration
public class GatewayConfig {

    // Custom filter (GlobalFilter)
    @Bean
    public GlobalFilter authFilter(AuthOrchestrator orchestrator) {
        return new AuthGlobalFilter(orchestrator);
    }

    // ObjectMapper (optional customization)
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    // CORS filter for WebFlux (alternative to YAML globalcors)
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
