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

@RestController
public class FallbackController {
    @RequestMapping("/fallback")
    public Mono<String> fallback() {
        return Mono.just("Service is unavailable, this is a fallback");
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
