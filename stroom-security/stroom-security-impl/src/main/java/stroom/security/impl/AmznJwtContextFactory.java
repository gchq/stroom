package stroom.security.impl;

import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.http.HttpStatus;
import org.jose4j.base64url.SimplePEMEncoder;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class AmznJwtContextFactory implements JwtContextFactory {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AmznJwtContextFactory.class);
    private static final String AMZN_OIDC_ACCESS_TOKEN_HEADER = "x-amzn-oidc-accesstoken";
    private static final String AMZN_OIDC_IDENTITY_HEADER = "x-amzn-oidc-identity";
    private static final String AMZN_OIDC_DATA_HEADER = "x-amzn-oidc-data";

    private final ResolvedOpenIdConfig openIdConfig;
    private final WebTargetFactory webTargetFactory;
    private final Map<String, PublicKey> publicKeyMap = new ConcurrentHashMap<>();

    @Inject
    AmznJwtContextFactory(final ResolvedOpenIdConfig openIdConfig,
                          final WebTargetFactory webTargetFactory) {
        this.openIdConfig = openIdConfig;
        this.webTargetFactory = webTargetFactory;
    }

    @Override
    public Optional<JwtContext> getJwtContext(final HttpServletRequest request) {
        LOGGER.debug(() -> AMZN_OIDC_ACCESS_TOKEN_HEADER + "=" + request.getHeader(AMZN_OIDC_ACCESS_TOKEN_HEADER));
        LOGGER.debug(() -> AMZN_OIDC_IDENTITY_HEADER + "=" + request.getHeader(AMZN_OIDC_IDENTITY_HEADER));
        LOGGER.debug(() -> AMZN_OIDC_DATA_HEADER + "=" + request.getHeader(AMZN_OIDC_DATA_HEADER));

        final Optional<String> optionalJws = JwtUtil.getJwsFromHeader(request, AMZN_OIDC_DATA_HEADER)
                .or(() -> JwtUtil.getJwsFromHeader(request, HttpHeaders.AUTHORIZATION));
        return optionalJws
                .flatMap(this::getJwtContext)
                .or(() -> {
                    LOGGER.debug(() -> "No JWS found in headers in request to " + request.getRequestURI());
                    return Optional.empty();
                });
    }

    @Override
    public Optional<JwtContext> getJwtContext(final String jws) {
        try {
            LOGGER.debug(() -> "jws=" + jws);

            // Step 1: Get the key id from JWT headers (the kid field)
            final String[] parts = jws.split("\\.");
            final String header = parts[0];
            final String payload = parts[1];
            final String signature = parts[2];

            LOGGER.debug(() -> "header=" + header + ", payload=" + payload + ", signature=" + signature);

            final String decodedHeader = new String(Base64.getDecoder().decode(header), StandardCharsets.UTF_8);

            LOGGER.debug(() -> "decodedHeader=" + decodedHeader);

            ObjectMapper objectMapper = new ObjectMapper();
            final Map<?, ?> map = objectMapper.readValue(decodedHeader, Map.class);
            final String kid = (String) map.get("kid");

            LOGGER.debug(() -> "kid=" + kid);

            // Step 2: Get the public key from regional endpoint
            final String uri = openIdConfig.getJwksUri() + "/" + kid;
            LOGGER.debug(() -> "uri=" + uri);

            final PublicKey publicKey = getPublicKey(uri);
            Objects.requireNonNull(publicKey, "Couldn't get public key");

            final JwtConsumerBuilder builder = new JwtConsumerBuilder()
                    .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims to account for clock skew
                    .setRequireSubject() // the JWT must have a subject claim
                    .setVerificationKey(publicKey)
                    .setRelaxVerificationKeyValidation() // relaxes key length requirement
                    .setExpectedIssuer(openIdConfig.getIssuer());
            final JwtConsumer jwtConsumer = builder.build();
            return Optional.ofNullable(jwtConsumer.process(jws));
        } catch (final RuntimeException | InvalidJwtException | IOException e) {
            LOGGER.debug(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private PublicKey getPublicKey(final String uri) {
        // See if we can get the public key from the cache.
        final PublicKey cached = publicKeyMap.get(uri);
        if (cached != null) {
            return cached;
        }

        LOGGER.debug(() -> "Getting public key from \"" + uri + "\"");
        final Response res = webTargetFactory
                .create(uri)
                .request()
                .get();
        if (res.getStatus() == HttpStatus.OK_200) {
            final String pubKey = res.readEntity(String.class);
            LOGGER.debug(() -> "Received public key \"" + pubKey + "\"");

            // The public key is PEM format.
            final PublicKey publicKey = decodePublicKey(pubKey, "EC");

            // Cache for next time.
            if (publicKey != null) {
                publicKeyMap.put(uri, publicKey);
            }

            return publicKey;

        } else {
            throw new RuntimeException("Unable to retrieve public key from \"" +
                    uri +
                    "\"" +
                    res.getStatus() +
                    ": " +
                    res.readEntity(String.class));
        }
    }


    private PublicKey decodePublicKey(final String pem, final String alg) {
        PublicKey publicKey = null;

        try {
            // decode to its constituent bytes
            String publicKeyPEM = pem;
            publicKeyPEM = publicKeyPEM.replace("-----BEGIN PUBLIC KEY-----\n", "");
            publicKeyPEM = publicKeyPEM.replace("-----END PUBLIC KEY-----", "");

            byte[] publicKeyBytes = SimplePEMEncoder.decode(publicKeyPEM);

            // create a key object from the bytes
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(alg);
            publicKey = keyFactory.generatePublic(keySpec);

        } catch (final RuntimeException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            LOGGER.error(alg + " " + e.getMessage(), e);
        }

        return publicKey;
    }

    @Override
    public boolean isTokenExpectedInRequest() {
        return true;
    }
}
