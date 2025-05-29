@Component
public class JwtValidationFilter implements GlobalFilter, Ordered {

    @Autowired
    private MyTokenService tokenService; // Your custom library

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7); // Remove "Bearer "

        try {
            // Validate JWT using jose4j
            JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                    .setRequireExpirationTime()
                    .setAllowedClockSkewInSeconds(30)
                    .setRequireSubject()
                    .setVerificationKey(/* your public key here */)
                    .build();

            JwtClaims claims = jwtConsumer.processToClaims(token);

            // If valid, call custom token generator
            TokenResponse newToken = tokenService.getToken(); // access_token, token_type, expires_in

            // Set new token in response header
            exchange.getResponse().getHeaders().add("X-New-Token", newToken.getAccessToken());

            return chain.filter(exchange);

        } catch (InvalidJwtException e) {
            return unauthorized(exchange);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100; // High priority
    }
}
