package stroom.security.common.impl;

import stroom.security.api.exception.AuthenticationException;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.NullSafe;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.eclipse.jetty.http.HttpStatus;
import org.jose4j.base64url.SimplePEMEncoder;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.jose4j.keys.resolvers.VerificationKeyResolver;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.AbstractMap.SimpleEntry;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

@Singleton // for ObjectMapper and cache
public class StandardJwtContextFactory implements JwtContextFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StandardJwtContextFactory.class);

    // TODO: 24/02/2023 These header keys ought to be in config
    static final String AMZN_OIDC_ACCESS_TOKEN_HEADER = "x-amzn-oidc-accesstoken";
    static final String AMZN_OIDC_IDENTITY_HEADER = "x-amzn-oidc-identity";
    static final String AMZN_OIDC_DATA_HEADER = "x-amzn-oidc-data";
    static final String AMZN_OIDC_SIGNER_HEADER_KEY = "signer";
    static final Pattern AMZN_OIDC_SIGNER_SPLIT_PATTERN = Pattern.compile(":");
//    static final String AMZN_OIDC_SIGNER_HEADER_PREFIX = "arn:aws:";

    private static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;
    private static final String CACHE_NAME = "AWS Public Key Cache";

    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;
    private final OpenIdPublicKeysSupplier openIdPublicKeysSupplier;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;

    // Stateful things
    private volatile LoadingCache<String, PublicKey> awsPublicKeyCache = null; // uri => publicKey
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public StandardJwtContextFactory(final Provider<OpenIdConfiguration> openIdConfigurationProvider,
                                     final OpenIdPublicKeysSupplier openIdPublicKeysSupplier,
                                     final DefaultOpenIdCredentials defaultOpenIdCredentials) {
        this.openIdConfigurationProvider = openIdConfigurationProvider;
        this.openIdPublicKeysSupplier = openIdPublicKeysSupplier;
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
    }

    private LoadingCache<String, PublicKey> createAwsPublicKeyCache() {
        LOGGER.info("Creating cache for AWS public keys");
        // TODO: 24/02/2023 Probably ought to come from config
        // Need to use caffeine rather than StroomCache as this is used in proxy which
        // doesn't have it
        final LoadingCache<String, PublicKey> awsPublicKeyCache;
        awsPublicKeyCache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterAccess(Duration.ofHours(1))
                .build(this::fetchAwsPublicKey);

        final Timer timer = new Timer("AWS public key cache eviction timer", true);
        timer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        awsPublicKeyCache.cleanUp();
                    }
                },
                Duration.ofMinutes(1).toMillis(),
                Duration.ofMinutes(1).toMillis());
        return awsPublicKeyCache;
    }

    @Override
    public boolean hasToken(final HttpServletRequest request) {
        return getTokenFromHeader(request)
                .isPresent();
    }

    @Override
    public void removeAuthorisationEntries(final Map<String, String> headers) {
        if (NullSafe.hasEntries(headers)) {
            headers.remove(AUTHORIZATION_HEADER);
            headers.remove(AMZN_OIDC_ACCESS_TOKEN_HEADER);
            headers.remove(AMZN_OIDC_DATA_HEADER);
            headers.remove(AMZN_OIDC_IDENTITY_HEADER);
        }
    }

    @Override
    public Map<String, String> createAuthorisationEntries(final String accessToken) {
        // Should be common to both internal and external IDPs
        if (NullSafe.isBlankString(accessToken)) {
            return Collections.emptyMap();
        } else {
            // TODO: 07/12/2022 Do we need to set the amzn headers?
            return Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
    }

    @Override
    public Optional<JwtContext> getJwtContext(final HttpServletRequest request) {

        if (LOGGER.isDebugEnabled()) {
            // Only output non-null ones
            final String headers = Stream.of(
                            AUTHORIZATION_HEADER,
                            AMZN_OIDC_ACCESS_TOKEN_HEADER,
                            AMZN_OIDC_IDENTITY_HEADER,
                            AMZN_OIDC_DATA_HEADER)
                    .map(key -> new SimpleEntry<>(key, request.getHeader(key)))
                    .filter(entry -> entry.getValue() != null)
                    .map(keyValue -> "\n  " + keyValue.getKey() + ": '" + keyValue.getValue() + "'")
                    .collect(Collectors.joining(" "));
            LOGGER.debug("getJwtContext called for request with uri: {}, headers:{}",
                    request.getRequestURI(), headers);
        }

        final Optional<HeaderToken> optionalJwt = getTokenFromHeader(request);

        return optionalJwt
                .flatMap(headerToken -> {
                    if (AMZN_OIDC_DATA_HEADER.equals(headerToken.header)) {
                        // Could move parseJws into getAwsJwtContext
                        final JwsParts jwsParts = parseJws(headerToken.jwt);
                        return getAwsJwtContext(jwsParts);
                    } else {
                        return getStandardJwtContext(headerToken.jwt);
                    }
                })
                .or(() -> {
                    LOGGER.debug(() -> "No JWS found in headers in request to " + request.getRequestURI());
                    return Optional.empty();
                });
    }

    private Optional<HeaderToken> getTokenFromHeader(final HttpServletRequest request) {
        // When using an AWS ELB/ALB that is integrated with the IDP, the ELB/ALB will
        // do the auth flow and just pass us a different header containing the claims obtained
        // from the IDP. See
        // https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-authenticate-users.html
        // We may be dealing with requests of either form
        return JwtUtil.getJwsFromHeader(request, AMZN_OIDC_DATA_HEADER)
                .map(jws -> new HeaderToken(AMZN_OIDC_DATA_HEADER, jws))
                .or(() ->
                        JwtUtil.getJwsFromHeader(request, AUTHORIZATION_HEADER)
                                .map(jws -> new HeaderToken(AUTHORIZATION_HEADER, jws)));
    }

    private JwsParts parseJws(final String jws) {
        LOGGER.debug(() -> "jws=" + jws);

        // Step 1: Get the key id from JWT headers (the kid field)
        final String[] parts = jws.split("\\.");
        final String header = parts[0];
        final String payload = parts[1];
        final String signature = parts[2];

        LOGGER.debug(() -> "header=" + header + ", payload=" + payload + ", signature=" + signature);

        final String decodedHeader;
        try {
            decodedHeader = new String(Base64.getDecoder().decode(header), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("jws is not in BASE64 format: {}",
                    LogUtil.exceptionMessage(e)), e);
        }

        LOGGER.debug("decodedHeader: {}", decodedHeader);

        try {
            final Map<String, String> headerMap = objectMapper.readValue(
                    decodedHeader,
                    new TypeReference<HashMap<String, String>>() {
                    });
            LOGGER.debug("headerMap: {}", headerMap);

            return new JwsParts(
                    jws,
                    header,
                    payload,
                    signature,
                    headerMap);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(LogUtil.message("Error parsing header as json: {}. decodedHeader:\n{}",
                    LogUtil.exceptionMessage(e), decodedHeader), e);
        }
    }

    private Optional<JwtContext> getAwsJwtContext(final JwsParts jwsParts) {
        LOGGER.debug("getAwsJwtContext called for jwsParts: {}", jwsParts);
        final PublicKey publicKey = getAwsPublicKey(jwsParts);
        Objects.requireNonNull(publicKey, "Couldn't get public key");

        try {
            final JwtConsumerBuilder builder = new JwtConsumerBuilder()
                    .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims
                    // to account for clock skew
                    .setRequireSubject() // the JWT must have a subject claim
                    .setVerificationKey(publicKey)
                    .setRelaxVerificationKeyValidation() // relaxes key length requirement
                    .setExpectedIssuer(openIdConfigurationProvider.get().getIssuer());
            final JwtConsumer jwtConsumer = builder.build();
            return Optional.ofNullable(jwtConsumer.process(jwsParts.jws));

        } catch (Exception e) {
            LOGGER.debug(e::getMessage, e);
            throw new RuntimeException(LogUtil.message("Error getting jwt context for AWS JWT: {}",
                    LogUtil.exceptionMessage(e)), e);
        }
    }

    private Optional<JwtContext> getStandardJwtContext(final String jwt) {
        LOGGER.debug("getStandardJwtContext called for jwt: {}", jwt);
        Objects.requireNonNull(jwt, "Null JWS");

        try {
            LOGGER.debug(() -> "Verifying token...");
            final JwtConsumer jwtConsumer = newJwtConsumer();
            final JwtContext jwtContext = jwtConsumer.process(jwt);

            LOGGER.debug(() -> LogUtil.message("Verified token - {}: '{}', {}: '{}'",
                    OpenId.CLAIM__SUBJECT,
                    JwtUtil.getClaimValue(jwtContext, OpenId.CLAIM__SUBJECT).orElse(""),
                    OpenId.CLAIM__PREFERRED_USERNAME,
                    JwtUtil.getClaimValue(jwtContext, OpenId.CLAIM__PREFERRED_USERNAME).orElse("")));

            // TODO : @66 Check against blacklist to see if token has been revoked. Blacklist
            //  is a list of JWI (JWT IDs) on auth service. Only tokens with `jwi` claims are API
            //  keys so only those tokens need checking against the blacklist cache.

//            if (checkTokenRevocation) {
//                LOGGER.debug(() -> "Checking token revocation status in remote auth service...");
//                final String userId = getUserIdFromToken(jws);
//                isRevoked = userId == null;
//            }

            return Optional.ofNullable(jwtContext);

        } catch (final RuntimeException | InvalidJwtException e) {
            LOGGER.debug(() -> "Unable to verify token: " + e.getMessage(), e);
            throw new AuthenticationException(e.getMessage(), e);
        }
    }

    /**
     * Verify the JSON Web Signature and then extract the user identity from it
     */
    @Override
    public Optional<JwtContext> getJwtContext(final String jwt) {
        LOGGER.debug("getJwtContext called for jwt: {}", jwt);
        // We don't know if this is signed by the IDP or aws so crack it open
//        final JwsParts jwsParts = parseJws(jwt);
//        if (jwsParts.getHeaderValue(AMZN_OIDC_SIGNER_HEADER_KEY)
//                .filter(val -> !val.isBlank())
//                .filter(val -> val.startsWith(AMZN_OIDC_SIGNER_HEADER_PREFIX))
//                .isPresent()) {
//            // This jwt was signed by AWS
//            return getAwsJwtContext(jwsParts);
//        } else {

        // There is no request with this so this is not an AWS signed token.
        return getStandardJwtContext(jwt);
//        }
    }

    @Override
    public Optional<JwtContext> getJwtContext(final String jwt, final boolean doVerification) {
        LOGGER.debug("getJwtContext called for doVerification: {}, jwt: {}", doVerification, jwt);
        Optional<JwtContext> optJwtContext = Optional.empty();
        if (doVerification) {
            optJwtContext = getJwtContext(jwt);
        } else {
            final JwtConsumer simpleJwtConsumer = new JwtConsumerBuilder()
                    .setSkipSignatureVerification()
                    .setSkipDefaultAudienceValidation()
                    .build();
            try {
                optJwtContext = Optional.of(simpleJwtConsumer.process(jwt));
            } catch (Exception e) {
                LOGGER.debug(() -> "Unable to extract token: " + e.getMessage(), e);
            }
        }
        return optJwtContext;
    }

    private JwtConsumer newJwtConsumer() {
        // If we don't have a JWK we can't create a consumer to verify anything.
        // Why might we not have one? If the remote authentication service was down when Stroom started
        // then we wouldn't. It might not be up now but we're going to try and fetch it.
        final JsonWebKeySet publicJsonWebKey = openIdPublicKeysSupplier.get();

        final VerificationKeyResolver verificationKeyResolver = new JwksVerificationKeyResolver(
                publicJsonWebKey.getJsonWebKeys());
        final OpenIdConfiguration openIdConfiguration = openIdConfigurationProvider.get();

        final boolean useTestCreds = NullSafe.test(
                openIdConfiguration,
                OpenIdConfiguration::getIdentityProviderType,
                IdpType.TEST_CREDENTIALS::equals);

        final String issuer = useTestCreds
                ? defaultOpenIdCredentials.getOauth2Issuer()
                : openIdConfiguration.getIssuer();

        LOGGER.debug("Expecting issuer: {}", issuer);

        final JwtConsumerBuilder builder = new JwtConsumerBuilder()
                .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims to account
                //                                   for clock skew
                .setRequireSubject() // the JWT must have a subject claim
                .setVerificationKeyResolver(verificationKeyResolver)
                .setRelaxVerificationKeyValidation() // relaxes key length requirement
//                .setJwsAlgorithmConstraints(// only allow the expected signature algorithm(s) in the given context
//                        new AlgorithmConstraints(
//                                AlgorithmConstraints.ConstraintType.WHITELIST, // which is only RS256 here
//                                AlgorithmIdentifiers.RSA_USING_SHA256))
                .setExpectedIssuer(issuer);

        if (openIdConfiguration.isValidateAudience()) {
            // aud does not appear in access tokens by default it seems so make the check optional
            final String clientId = useTestCreds
                    ? defaultOpenIdCredentials.getOauth2ClientId()
                    : openIdConfiguration.getClientId();
            builder.setExpectedAudience(clientId);
        } else {
            builder.setSkipDefaultAudienceValidation();
        }
        return builder.build();
    }

    private PublicKey getAwsPublicKey(final JwsParts jwsParts) {
        final String uri = getAwsPublicKeyUri(jwsParts);

        // Lazy initialise the cache and its timer in case we never deal with aws keys
        if (awsPublicKeyCache == null) {
            synchronized (this) {
                if (awsPublicKeyCache == null) {
                    awsPublicKeyCache = createAwsPublicKeyCache();
                }
            }
        }

        return awsPublicKeyCache.get(uri);
    }

    // pkg private for testing
    static String getAwsPublicKeyUri(final JwsParts jwsParts) {
        final String signer = jwsParts.getHeaderValue(AMZN_OIDC_SIGNER_HEADER_KEY)
                .orElseThrow(() -> new RuntimeException(LogUtil.message("Missing '{}' key in jws header {}",
                        AMZN_OIDC_SIGNER_HEADER_KEY, jwsParts.header)));
        final String keyId = jwsParts.getHeaderValue(OpenId.KEY_ID)
                .orElseThrow(() -> new RuntimeException(LogUtil.message("Missing '{}' key in jws header {}",
                        OpenId.KEY_ID, jwsParts.header)));

        if (NullSafe.isBlankString(signer)) {
            throw new RuntimeException(LogUtil.message("Blank value for '{}' key in jws header {}",
                    AMZN_OIDC_SIGNER_HEADER_KEY, jwsParts.header));
        }

        // Signer is an Amazon Resource Name of the form:
        // arn:partition:service:region:account-id:resource-id
        final String[] signerParts = AMZN_OIDC_SIGNER_SPLIT_PATTERN.split(signer);
        if (signerParts.length < 4) {
            throw new RuntimeException(LogUtil.message("Unable to parse value for '{}' key in jws header {}",
                    AMZN_OIDC_SIGNER_HEADER_KEY, signer));
        }
        final String awsRegion = signerParts[3];

        // TODO: 24/02/2023 Ought to come from config
        final String uri = LogUtil.message("https://public-keys.auth.elb.{}.amazonaws.com/{}",
                awsRegion, keyId);
        return uri;
    }

    private PublicKey fetchAwsPublicKey(final String uri) {

        LOGGER.debug(() -> LogUtil.message("Fetching AWS public key from uri: {}, current cache size: {}",
                uri, awsPublicKeyCache.estimatedSize()));
        final Client client = ClientBuilder.newClient();
        // Don't use injected WebTargetFactory as that slaps a token on which we don't want in
        // this case as it is an unauthenticated endpoint
        final WebTarget target = client.target(uri);
        final Response res = target
                .request()
                .get();
        if (res.getStatus() == HttpStatus.OK_200) {
            final String pubKey = res.readEntity(String.class);
            LOGGER.debug(() -> "Received public key \"" + pubKey + "\"");

            // The public key is PEM format.
            return decodePublicKey(pubKey, "EC");
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


    // --------------------------------------------------------------------------------


    private record HeaderToken(
            String header,
            String jwt) {

    }


    // --------------------------------------------------------------------------------


    // Pkg private for testing
    record JwsParts(
            String jws,
            String header,
            String payload,
            String signature,
            Map<String, String> headerMap) {

        Optional<String> getHeaderValue(final String key) {
            return Optional.ofNullable(headerMap.get(key));
        }
    }
}
