/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.common.impl;

import stroom.security.api.exception.AuthenticationException;
import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.concurrent.CachedValue;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.jersey.JerseyClientName;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.string.TemplateUtil;
import stroom.util.string.TemplateUtil.Templator;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.jetty.http.HttpStatus;
import org.jose4j.base64url.SimplePEMEncoder;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwt.JwtClaims;
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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class for getting the JWT context when Stroom is integrated with an external Open ID connect
 * identity provider, e.g. keycloak.
 * <p>
 * Also handles the special case of AWS deployments that use a
 * load balancer which is configured to do the authentication and interaction with the IDP.
 * This case uses AWS specific headers, so requires different behaviour for user code flow.
 * </p>
 * <p>
 * See <a href="https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-authenticate-users.html">AWS Docs</a>
 * for det
 * </p>
 */
@Singleton // for awsPublicKeyCache
public class StandardJwtContextFactory implements JwtContextFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StandardJwtContextFactory.class);

    // TODO: 24/02/2023 These header keys ought to be in config
    //  Do as part of https://github.com/gchq/stroom/issues/5070
    /**
     * The access token from the token endpoint, in plain text.
     */
    static final String AMZN_OIDC_ACCESS_TOKEN_HEADER = "x-amzn-oidc-accesstoken";
    /**
     * The subject field (sub) from the user info endpoint, in plain text.
     */
    static final String AMZN_OIDC_IDENTITY_HEADER = "x-amzn-oidc-identity";
    /**
     * The user claims, in JSON web tokens (JWT) format.
     */
    static final String AMZN_OIDC_DATA_HEADER = "x-amzn-oidc-data";
    static final String AMZN_OIDC_SIGNER_PREFIX = "arn:";
    static final String SIGNER_HEADER_KEY = "signer";
    static final String AMZN_OIDC_SIGNER_SPLIT_CHAR = ":";
    static final Pattern AMZN_REGION_PATTERN = Pattern.compile("^[a-z0-9-]+$");
    static final String AWS_REGION_TEMPLATE_VARIABLE = "awsRegion";
    static final String KEY_ID_TEMPLATE_VARIABLE = "keyId";

    private static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;

    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;
    private final OpenIdPublicKeysSupplier openIdPublicKeysSupplier;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final JerseyClientFactory jerseyClientFactory;

    // Stateful things
    // Not clear whether AWS re-uses public keys or not so this may not be needed
    private volatile LoadingCache<String, PublicKey> awsPublicKeyCache = null; // uri => publicKey
    private final CachedValue<Templator, String> awsPublicKeyUriTemplator;

    @Inject
    public StandardJwtContextFactory(final Provider<OpenIdConfiguration> openIdConfigurationProvider,
                                     final OpenIdPublicKeysSupplier openIdPublicKeysSupplier,
                                     final DefaultOpenIdCredentials defaultOpenIdCredentials,
                                     final JerseyClientFactory jerseyClientFactory) {
        this.openIdConfigurationProvider = openIdConfigurationProvider;
        this.openIdPublicKeysSupplier = openIdPublicKeysSupplier;
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
        this.jerseyClientFactory = jerseyClientFactory;

        this.awsPublicKeyUriTemplator = CachedValue.builder()
                .withMaxCheckIntervalMinutes(1)
                .withStateSupplier(() ->
                        NullSafe.nonBlankStringElse(openIdConfigurationProvider.get().getPublicKeyUriPattern(),
                                AbstractOpenIdConfig.DEFAULT_AWS_PUBLIC_KEY_URI_TEMPLATE))
                .withValueFunction(template -> TemplateUtil.parseTemplate(template))
                .build();
    }

    private LoadingCache<String, PublicKey> createAwsPublicKeyCache() {
        LOGGER.info("Creating cache for AWS public keys");
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
                        LOGGER.debug("Evicting expired AWS public keys from the cache");
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
            return Map.of(
                    HttpHeaders.AUTHORIZATION,
                    JwtUtil.BEARER_PREFIX + accessToken);
        }
    }

    public Map<String, String> createAuthorisationEntries(final String accessToken, final JwtClaims jwtClaims) {
        // Should be common to both internal and external IDPs
        if (NullSafe.isBlankString(accessToken)) {
            return Collections.emptyMap();
        } else if (jwtClaims == null) {
            return Map.of(
                    HttpHeaders.AUTHORIZATION,
                    JwtUtil.BEARER_PREFIX + accessToken);
        } else {

            return Map.of(
                    HttpHeaders.AUTHORIZATION,
                    JwtUtil.BEARER_PREFIX + accessToken);
        }
    }

    @Override
    public Optional<JwtContext> getJwtContext(final HttpServletRequest request) {

        if (LOGGER.isTraceEnabled()) {
            // Only output non-null ones. Probably the only useful one is the ID one as the rest are base64 encoded
            final String headers = Stream.of(
                            AUTHORIZATION_HEADER,
                            AMZN_OIDC_ACCESS_TOKEN_HEADER,
                            AMZN_OIDC_IDENTITY_HEADER,
                            AMZN_OIDC_DATA_HEADER)
                    .map(key -> new SimpleEntry<>(key, request.getHeader(key)))
                    .filter(entry -> entry.getValue() != null)
                    .map(keyValue -> "\n  " + keyValue.getKey() + ": '" + keyValue.getValue() + "'")
                    .collect(Collectors.joining(" "));
            LOGGER.trace("getJwtContext called for request with uri: {}, headers:{}",
                    request.getRequestURI(), headers);
        }

        return getTokenFromHeader(request)
                .flatMap(this::getJwtContextFromHeaderToken)
                .or(() -> {
                    LOGGER.debug(() -> "No JWS found in headers in request to " + request.getRequestURI());
                    return Optional.empty();
                });
    }

    private boolean isAwsSignedToken(final HeaderToken headerToken, final JwsParts jwsParts) {
        if (AMZN_OIDC_DATA_HEADER.equals(headerToken.header)) {
            LOGGER.debug("Found header {}", AMZN_OIDC_DATA_HEADER);
            // Request came from an AWS load balancer that did the auth flow
            return true;
        } else {
            // Request may have come from another stroom node that has passed on an AWS signed
            // token, but not in the special AWS header
            return isAwsSignedToken(jwsParts);
        }
    }

    private boolean isAwsSignedToken(final JwsParts jwsParts) {
        // Request may have come from another stroom node that has passed on an AWS signed
        // token, but not in the special AWS header
        return jwsParts.getHeaderValue(SIGNER_HEADER_KEY)
                .filter(val -> {
                    LOGGER.debug("{} is {}", SIGNER_HEADER_KEY, val);
                    return val.startsWith(AMZN_OIDC_SIGNER_PREFIX);
                })
                .isPresent();
    }

    private Optional<JwtContext> getJwtContextFromHeaderToken(final HeaderToken headerToken) {
        final Optional<JwtContext> optJwtContext;
        final JwsParts jwsParts = parseJws(headerToken.jwt);

        if (isAwsSignedToken(headerToken, jwsParts)) {
            optJwtContext = getAwsJwtContext(jwsParts);
        } else {
            optJwtContext = getStandardJwtContext(headerToken.jwt);
        }

        LOGGER.debug(() -> LogUtil.message("jwtClaims:\n{}",
                optJwtContext.map(JwtContext::getJwtClaims)
                        .map(jwtClaims -> jwtClaims.getClaimsMap()
                                .entrySet()
                                .stream()
                                .sorted(Entry.comparingByKey())
                                .map(entry ->
                                        "  " + entry.getKey() + ": '" + entry.getValue().toString() + "'")
                                .collect(Collectors.joining("\n")))
                        .orElse("  <empty>")));

        return optJwtContext;
    }

    private Optional<HeaderToken> getTokenFromHeader(final HttpServletRequest request) {
        // When using an AWS ELB/ALB that is integrated with the IDP, the ELB/ALB will
        // do the auth flow and just pass us a different header containing the claims obtained
        // from the IDP. See
        // https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-authenticate-users.html
        // We may be dealing with requests of either form
        if (LOGGER.isDebugEnabled()) {
            // This will log the AWS identity if there is one
            JwtUtil.getJwsFromHeader(request, AMZN_OIDC_IDENTITY_HEADER);
        }
        return JwtUtil.getJwsFromHeader(request, AMZN_OIDC_DATA_HEADER)
                .map(jws -> new HeaderToken(AMZN_OIDC_DATA_HEADER, jws))
                .or(() ->
                        JwtUtil.getJwsFromHeader(request, AUTHORIZATION_HEADER)
                                .map(jws -> new HeaderToken(AUTHORIZATION_HEADER, jws)));
    }


    private JwsParts parseJws(final String jws) {
        LOGGER.debug("jws: {}", jws);

        try {
            // Step 1: Get the key id from JWT headers (the kid field)
            // Regex split on single char will not use regex so no need to pre-compile
            final String[] parts = jws.split("\\.");
            final String header = parts[0];
            final String payload = parts[1];
            final String signature = parts[2];

            LOGGER.debug("""
                    parseJws()
                    header: {}
                    payload: {}
                    signature: {}""", header, payload, signature);

            return new JwsParts(jws, header, payload, signature);
        } catch (final Exception e) {
            LOGGER.debug("Unable to parse '{}' as a JWT", jws, e);
            throw new AuthenticationException(LogUtil.message(
                    "Error parsing token as a JSON Web Token: {}", e.getMessage()));
        }
    }

    private Optional<JwtContext> getAwsJwtContext(final JwsParts jwsParts) {
        LOGGER.debug("getAwsJwtContext called for jwsParts: {}", jwsParts);
        final PublicKey publicKey = getAwsPublicKey(jwsParts);
        Objects.requireNonNull(publicKey, "Couldn't get public key");

        final String[] validIssuers = getValidIssuers();

        try {
            final JwtConsumerBuilder builder = new JwtConsumerBuilder()
                    .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims
                    // to account for clock skew
                    .setRequireSubject() // the JWT must have a subject claim
                    .setVerificationKey(publicKey)
                    .setRelaxVerificationKeyValidation() // relaxes key length requirement
                    .setExpectedIssuers(true, validIssuers);
            final JwtConsumer jwtConsumer = builder.build();
            return Optional.ofNullable(jwtConsumer.process(jwsParts.jws));

        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
            throw new RuntimeException(LogUtil.message("Error getting jwt context for AWS JWT: {}",
                    LogUtil.exceptionMessage(e)), e);
        }
    }

    private String[] getValidIssuers() {
        final OpenIdConfiguration openIdConfiguration = openIdConfigurationProvider.get();
        if (NullSafe.isBlankString(openIdConfiguration.getIssuer())) {
            throw new RuntimeException(LogUtil.message(
                    "'issuer' is not defined in the IDP's or Stroom's configuration"));
        }
        final String[] validIssuers = Stream.concat(
                        Stream.of(openIdConfiguration.getIssuer()),
                        NullSafe.stream(openIdConfiguration.getValidIssuers()))
                .filter(Objects::nonNull)
                .filter(str -> !str.isBlank())
                .distinct()
                .toArray(String[]::new);
        LOGGER.debug(() -> LogUtil.message("Valid issuers:\n{}", String.join("\n", validIssuers)));
        return validIssuers;
    }

    private Optional<JwtContext> getStandardJwtContext(final String jwt) {
        LOGGER.debug("getStandardJwtContext called for jwt: {}", jwt);
        Objects.requireNonNull(jwt, "Null JWS");

        try {
            LOGGER.debug(() -> "Verifying token...");
            final JwtConsumer jwtConsumer = newJwtConsumer();
            final JwtContext jwtContext = jwtConsumer.process(jwt);

            if (LOGGER.isDebugEnabled()) {
                final String uniqueIdentityClaim = openIdConfigurationProvider.get().getUniqueIdentityClaim();
                final String userDisplayNameClaim = openIdConfigurationProvider.get().getUserDisplayNameClaim();
                final String uniqueId = NullSafe.isBlankString(uniqueIdentityClaim)
                        ? "<ERROR uniqueIdentityClaim not configured>"
                        : JwtUtil.getClaimValue(jwtContext, uniqueIdentityClaim).orElse(null);
                final String displayName = NullSafe.isBlankString(userDisplayNameClaim)
                        ? "<ERROR userDisplayNameClaim not configured>"
                        : JwtUtil.getClaimValue(jwtContext, userDisplayNameClaim).orElse(null);

                LOGGER.debug(() -> LogUtil.message("Verified token - {}: '{}', {}: '{}', aud: '{}'",
                        uniqueIdentityClaim,
                        uniqueId,
                        userDisplayNameClaim,
                        displayName,
                        JwtUtil.getClaimValue(jwtContext, OpenId.CLAIM__AUDIENCE)));
            }

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
        final JwsParts jwsParts = parseJws(jwt);
        if (isAwsSignedToken(jwsParts)) {
            return getAwsJwtContext(jwsParts);
        } else {
            return getStandardJwtContext(jwt);
        }
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
            } catch (final Exception e) {
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

        final String[] validIssuers = useTestCreds
                ? new String[]{defaultOpenIdCredentials.getOauth2Issuer()}
                : getValidIssuers();

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
                .setExpectedIssuers(true, validIssuers);

        final Set<String> allowedAudiences = openIdConfiguration.getAllowedAudiences();
        if (NullSafe.hasItems(allowedAudiences)) {
            // The IDP may not supply the aud claim
            builder.setExpectedAudience(
                    openIdConfiguration.isAudienceClaimRequired(),
                    allowedAudiences.toArray(String[]::new));
        } else {
            builder.setSkipDefaultAudienceValidation();
        }
        LOGGER.debug("validIssuers: {}, allowedAudiences: {}, audienceClaimRequired: {}, useTestCreds: {}",
                validIssuers,
                allowedAudiences,
                openIdConfiguration.isAudienceClaimRequired(),
                useTestCreds);
        return builder.build();
    }

    private PublicKey getAwsPublicKey(final JwsParts jwsParts) {
        final String uri = getAwsPublicKeyUri(
                jwsParts,
                openIdConfigurationProvider.get().getExpectedSignerPrefixes(),
                awsPublicKeyUriTemplator.getValue());

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
    static String getAwsPublicKeyUri(final JwsParts jwsParts,
                                     final Set<String> expectedSignerPrefixes,
                                     final Templator publicKeyUriTemplator) {

        final Map<String, String> headerValues = jwsParts.getHeaderValues(
                SIGNER_HEADER_KEY,
                OpenId.KEY_ID);

        final String signer = Optional.ofNullable(headerValues.get(SIGNER_HEADER_KEY))
                .orElseThrow(() -> new RuntimeException(LogUtil.message("Missing '{}' key in jws header {}",
                        SIGNER_HEADER_KEY, jwsParts.header)));

        // 'arn:aws:elasticloadbalancing:region-code:account-id:loadbalancer/app/load-balancer-name/load-balancer-id'
        // The LB ID is not known at provisioning time so validate signer against a set of valid prefixes
        // which should go at least up to the account ID.
        final boolean isExpectedSigner = NullSafe.stream(expectedSignerPrefixes)
                .anyMatch(expectedSignerPrefix -> {
                    if (expectedSignerPrefix == null || signer == null) {
                        return false;
                    } else {
                        return signer.startsWith(expectedSignerPrefix);
                    }
                });

        // Make sure the signer value is one we expect
        if (!isExpectedSigner) {
            throw new RuntimeException(LogUtil.message(
                    "The value for key '{}' in the JWS header '{}' does not match any of the values in the '{}' " +
                    "configuration property: [{}]. You need to set '{}' to the partial ARN(s) of the " +
                    "AWS load balancer that is handling authentication for Stroom. The partial " +
                    "ARN needs to include at least up to the account ID part.",
                    SIGNER_HEADER_KEY,
                    signer,
                    AbstractOpenIdConfig.PROP_NAME_EXPECTED_SIGNER_PREFIXES,
                    String.join(", ", NullSafe.set(expectedSignerPrefixes)),
                    AbstractOpenIdConfig.PROP_NAME_EXPECTED_SIGNER_PREFIXES));
        }

        final String keyId = NullSafe.string(headerValues.get(OpenId.KEY_ID));
        if (NullSafe.isBlankString(keyId)) {
            throw new RuntimeException(LogUtil.message("Missing '{}' key in jws header {}",
                    OpenId.KEY_ID, jwsParts.header));
        }
        final String awsRegion = NullSafe.string(extractAwsRegionFromSigner(signer));

        final String publicKeyUri = publicKeyUriTemplator.buildGenerator()
                .addReplacement(AWS_REGION_TEMPLATE_VARIABLE, awsRegion)
                .addReplacement(KEY_ID_TEMPLATE_VARIABLE, keyId)
                .generate();

        LOGGER.debug("publicKeyUriTemplator: '{}', awsRegion: '{}', keyId: '{}', publicKeyUri: '{}'",
                publicKeyUriTemplator, awsRegion, keyId, publicKeyUri);

        return publicKeyUri;
    }

    private static String extractAwsRegionFromSigner(final String signer) {
        if (NullSafe.isBlankString(signer)) {
            return null;
        } else {
            // Signer is an Amazon Resource Name of the form:
            // arn:partition:service:region:account-id:resource-id
            // No need to pre-compile the regex as split will not use regex for single chars
            final String[] signerParts = signer.split(AMZN_OIDC_SIGNER_SPLIT_CHAR);
            if (signerParts.length < 4) {
                throw new RuntimeException(LogUtil.message("Unable to parse value for '{}' key in JWS header {}",
                        SIGNER_HEADER_KEY, signer));
            }
            final String awsRegion = signerParts[3];

            if (!AMZN_REGION_PATTERN.matcher(awsRegion).matches()) {
                throw new RuntimeException(LogUtil.message(
                        "AWS region '{}' extracted from '{}' in property '{}' does not match " +
                        "pattern '{}'",
                        awsRegion,
                        signer,
                        AbstractOpenIdConfig.PROP_NAME_EXPECTED_SIGNER_PREFIXES,
                        AMZN_REGION_PATTERN.toString()));
            }

            return awsRegion;
        }
    }

    private PublicKey fetchAwsPublicKey(final String uri) {

        LOGGER.debug(() -> LogUtil.message("Fetching AWS public key from uri: {}, current cache size: {}",
                uri, awsPublicKeyCache.estimatedSize()));
        final Client client = jerseyClientFactory.getNamedClient(JerseyClientName.AWS_PUBLIC_KEYS);
        // Don't use injected WebTargetFactory as that slaps a token on which we don't want in
        // this case as it is an unauthenticated endpoint
        final WebTarget target = client.target(uri);
        try {
            final Response res = target
                    .request()
                    .get();
            final int status = res.getStatus();
            if (status == HttpStatus.OK_200) {
                final String pubKey = res.readEntity(String.class);
                LOGGER.debug("Received public key '{}'", pubKey);

                // The public key is PEM format.
                return decodeAwsPublicKey(pubKey, "EC");
            } else {
                final String msg = LogUtil.message(
                        "Unable to retrieve AWS public key from uri: '{}', status: {}, response: '{}'",
                        uri,
                        status,
                        LogUtil.swallowExceptions(() -> res.readEntity(String.class)));
                LOGGER.error(msg);
                throw new AuthenticationException();
            }
        } catch (final AuthenticationException e) {
            final String msg = LogUtil.message(
                    "Error retrieving AWS public key from uri: '{}' - {}",
                    uri,
                    LogUtil.exceptionMessage(e));
            LOGGER.error(msg, e);
            throw new AuthenticationException(msg, e);
        }
    }

    private PublicKey decodeAwsPublicKey(final String pem, final String alg) {
        PublicKey publicKey = null;

        try {
            // decode to its constituent bytes
            String publicKeyPEM = pem;
            publicKeyPEM = publicKeyPEM.replace("-----BEGIN PUBLIC KEY-----\n", "");
            publicKeyPEM = publicKeyPEM.replace("-----END PUBLIC KEY-----", "");

            final byte[] publicKeyBytes = SimplePEMEncoder.decode(publicKeyPEM);

            // create a key object from the bytes
            final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            final KeyFactory keyFactory = KeyFactory.getInstance(alg);
            publicKey = keyFactory.generatePublic(keySpec);

        } catch (final RuntimeException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            LOGGER.error("decodeAwsPublicKey() - alg: {}, message: {}", alg, LogUtil.exceptionMessage(e), e);
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
            String signature) {

//        Optional<String> getHeaderValue(final String key) {
//            return Optional.ofNullable(headerMap.get(key));
//        }

        private String base64Decode(final String input) {
            final String decoded;
            try {
                decoded = new String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8);
            } catch (final Exception e) {
                throw new RuntimeException(LogUtil.message("input is not in BASE64 format, input: '{}': {}",
                        input, LogUtil.exceptionMessage(e)), e);
            }

            LOGGER.debug("decodedHeader: {}", decoded);
            return decoded;
        }

        Map<String, String> getHeaderValues(final String... keys) {
            final String decodedHeader = base64Decode(header);
            return JsonUtil.getEntries(decodedHeader, keys);
        }

        Optional<String> getHeaderValue(final String key) {
            final String decodedHeader = base64Decode(header);
            return JsonUtil.getValue(decodedHeader, key);
        }
    }
}
