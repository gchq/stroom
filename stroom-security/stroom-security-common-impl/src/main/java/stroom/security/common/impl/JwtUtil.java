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

import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.servlet.http.HttpServletRequest;
import org.jose4j.json.internal.json_simple.parser.ContainerFactory;
import org.jose4j.json.internal.json_simple.parser.JSONParser;
import org.jose4j.json.internal.json_simple.parser.ParseException;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class JwtUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JwtUtil.class);

    public static final String BEARER_PREFIX = "Bearer ";
    private static final String CORP_PREFIX = "corp_";
    private static final String IDENTITIES = "identities";
    private static final String USER_ID = "userId";
    private static final String USER_NAME = "username";

    private static final ContainerFactory CONTAINER_FACTORY = new ContainerFactory() {
        public List<Object> creatArrayContainer() {
            return new ArrayList<>();
        }

        public Map<String, Object> createObjectContainer() {
            return new LinkedHashMap<>();
        }
    };

    private JwtUtil() {
    }

    /**
     * Get the JSON Web Signature from the specified request header
     */
    public static Optional<String> getJwsFromHeader(final HttpServletRequest request,
                                                    final String headerName) {
        return Optional.ofNullable(request.getHeader(headerName))
                .filter(str -> !str.isBlank())
                .map(str -> {
                    final String jws;
                    if (str.startsWith(BEARER_PREFIX)) {
                        // This chops out 'Bearer' so we get just the token.
                        jws = str.substring(BEARER_PREFIX.length());
                    } else {
                        jws = str;
                    }
                    LOGGER.debug(() ->
                            "Found auth header in request:\n" + headerName + ": " + jws);
                    return jws;
                });
    }

    public static String getEmail(final JwtClaims jwtClaims) {
        LOGGER.debug(() -> "Claim value " + OpenId.SCOPE__EMAIL + "=" + jwtClaims.getClaimValue(OpenId.SCOPE__EMAIL));
        return (String) jwtClaims.getClaimValue(OpenId.SCOPE__EMAIL);
    }

    public static String getUserName(final JwtClaims jwtClaims) {
        LOGGER.debug(() -> "Claim value " + USER_NAME + "=" + jwtClaims.getClaimValue(USER_NAME));
        final String userId = (String) jwtClaims.getClaimValue(USER_NAME);
        return removePrefix(userId);
    }

    /**
     * Gets the unique ID that links the identity on the IDP to the stroom_user.
     * Maps to the 'name' column in stroom_user table.
     */
    public static String getUniqueIdentity(final OpenIdConfiguration openIdConfiguration,
                                           final JwtClaims jwtClaims) {
        Objects.requireNonNull(openIdConfiguration);
        Objects.requireNonNull(jwtClaims);
        final String uniqueIdentityClaim = openIdConfiguration.getUniqueIdentityClaim();
        // Trim so that the value is consistent with the users created in our DB
        final String id = JwtUtil.getClaimValue(jwtClaims, uniqueIdentityClaim)
                .orElseThrow(() -> new RuntimeException(LogUtil.message(
                        "Expecting claims to contain configured uniqueIdentityClaim '{}' " +
                        "but it is not there, jwtClaims: {}",
                        uniqueIdentityClaim,
                        jwtClaims)));

        LOGGER.debug("uniqueIdentityClaim: {}, id: {}", uniqueIdentityClaim, id);

        return id;
    }

    /**
     * Gets the unique ID that links the identity on the IDP to the stroom_user.
     * Maps to the 'name' column in stroom_user table.
     */
    public static Optional<String> getUserDisplayName(final OpenIdConfiguration openIdConfiguration,
                                                      final JwtClaims jwtClaims) {
        Objects.requireNonNull(openIdConfiguration);
        Objects.requireNonNull(jwtClaims);
        final String userDisplayNameClaim = openIdConfiguration.getUserDisplayNameClaim();
        // Trim so that the value is consistent with the users created in our DB
        final Optional<String> userDisplayName = JwtUtil.getClaimValue(jwtClaims, userDisplayNameClaim)
                .map(String::trim);

        LOGGER.debug("userDisplayNameClaim: {}, userDisplayName: {}", userDisplayNameClaim, userDisplayName);

        return userDisplayName;
    }

    public static String removePrefix(final String userId) {
        if (userId != null) {
            final int index = userId.indexOf(CORP_PREFIX);
            if (index != -1) {
                return userId.substring(CORP_PREFIX.length());
            }
        }
        return userId;
    }

    public static String getUserIdFromIdentities(final JwtClaims jwtClaims) {
        String userId = null;
        final String identities = (String) jwtClaims.getClaimValue(IDENTITIES);
        if (identities != null) {
            userId = getUserIdFromIdentities(identities);
        }
        return userId;
    }

    @SuppressWarnings("unchecked")
    public static String getUserIdFromIdentities(final String identities) {
        String userId = null;
        try {
            if (identities != null) {
                final JSONParser jsonParser = new JSONParser();
                final List<Object> list = (List<Object>) jsonParser.parse(identities, CONTAINER_FACTORY);
                if (list != null && list.size() > 0) {
                    final Map<String, Object> identity = (Map<String, Object>) list.get(0);
                    userId = (String) identity.get(USER_ID);
                }
            }
        } catch (final ParseException | RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return userId;
    }

    public static String getSubject(final JwtClaims jwtClaims) {
        LOGGER.debug(() -> {
            try {
                return "Subject=" + jwtClaims.getSubject();
            } catch (final MalformedClaimException e) {
                LOGGER.debug(e.getMessage(), e);
            }
            return "MalformedClaimException";
        });

        String subject = null;
        try {
            subject = jwtClaims.getSubject();
        } catch (final MalformedClaimException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return subject;
    }

    /**
     * Get the trimmed value corresponding to claim
     */
    public static Optional<String> getClaimValue(final JwtContext jwtContext, final String claim) {
        return Optional.ofNullable(jwtContext)
                .map(JwtContext::getJwtClaims)
                .flatMap(jwtClaims ->
                        getClaimValue(jwtClaims, claim));
    }

    /**
     * Get the trimmed value corresponding to claim
     */
    public static Optional<String> getClaimValue(final JwtClaims jwtClaims, final String claim) {
        Objects.requireNonNull(claim);
        try {
            if (jwtClaims != null) {
                final String value = jwtClaims.getClaimValue(claim, String.class);
                final String trimmed = NullSafe.trim(value);
                return !trimmed.isEmpty()
                        ? Optional.of(trimmed)
                        : Optional.empty();
            } else {
                return Optional.empty();
            }
        } catch (final Exception e) {
            LOGGER.debug(() -> LogUtil.message("Error getting claim {}: {}", claim, e.getMessage()), e);
            return Optional.empty();
        }
    }
}
