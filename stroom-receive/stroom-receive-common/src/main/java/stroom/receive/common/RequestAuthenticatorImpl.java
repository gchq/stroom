package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.util.NullSafe;
import stroom.util.cert.CertificateExtractor;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;

public class RequestAuthenticatorImpl implements RequestAuthenticator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RequestAuthenticatorImpl.class);

    private final UserIdentityFactory userIdentityFactory;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    // Inject this so we can mock it for testing
    private final CertificateExtractor certificateExtractor;

    @Inject
    public RequestAuthenticatorImpl(final UserIdentityFactory userIdentityFactory,
                                    final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                                    final CertificateExtractor certificateExtractor) {
        this.userIdentityFactory = userIdentityFactory;
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.certificateExtractor = certificateExtractor;
    }

    @Override
    public UserIdentity authenticate(final HttpServletRequest request,
                                     final AttributeMap attributeMap) {
        Optional<UserIdentity> optUserIdentity = Optional.empty();
        final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();
        final boolean isAuthRequired = receiveDataConfig.isAuthenticationRequired();
        final boolean isTokenAuthEnabled = receiveDataConfig.isTokenAuthenticationEnabled();
        final boolean isCertAuthEnabled = receiveDataConfig.isCertificateAuthenticationEnabled();

        final Consumer<StroomStatusCode> throwFunc = code -> {
            throw new StroomStreamException(code, attributeMap);
        };

        // Try tokens first in preference
        final boolean foundToken = userIdentityFactory.hasAuthenticationToken(request);
        if (isTokenAuthEnabled) {
            if (foundToken) {
                optUserIdentity = userIdentityFactory.getApiUserIdentity(request);
                if (optUserIdentity.isEmpty() && isAuthRequired && !isCertAuthEnabled) {
                    throwFunc.accept(StroomStatusCode.CLIENT_TOKEN_NOT_AUTHORISED);
                }
            } else {
                // No token found
                if (isAuthRequired && !isCertAuthEnabled) {
                    throwFunc.accept(StroomStatusCode.CLIENT_TOKEN_REQUIRED);
                }
            }
        } else if (LOGGER.isDebugEnabled() && foundToken) {
            LOGGER.debug("Request has token but token authentication is not enabled. {}", attributeMap);
        }

        // Now try certs if
        final Optional<String> optCertCommonName = certificateExtractor.getCN(request);

        if (isCertAuthEnabled) {
            if (optUserIdentity.isEmpty()) {
                if (optCertCommonName.isPresent()) {
                    // Not much we can do with the cert. The user won't exist in stroom, so as long
                    // as the cert is trusted, we can get the CN and use that as the identity.
                    // Debatable whether this identity should be created in the UserIdentityFactory
                    optUserIdentity = optCertCommonName.map(CertificateUserIdentity::new);
                } else {
                    // No cert found
                    if (isAuthRequired) {
                        if (isTokenAuthEnabled) {
                            throwFunc.accept(StroomStatusCode.CLIENT_TOKEN_OR_CERT_REQUIRED);
                        } else {
                            throwFunc.accept(StroomStatusCode.CLIENT_CERTIFICATE_REQUIRED);
                        }
                    }
                }
            }
        } else if (LOGGER.isDebugEnabled() && optCertCommonName.isPresent()) {
            // Cert auth not enabled
            LOGGER.debug("Request has certificate but certificate authentication is not enabled. {}", attributeMap);
        }

        if (optUserIdentity.isEmpty() && !isAuthRequired) {
            // Debatable whether this identity should be created in the UserIdentityFactory
            optUserIdentity = Optional.of(UnauthenticatedUserIdentity.getInstance());
        }

        // Add identity attrs and remove auth ones
        processAttributes(attributeMap, optUserIdentity);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Authenticated user as {} of type {}",
                    optUserIdentity.map(Objects::toString)
                            .orElse("EMPTY"),
                    optUserIdentity.map(usr -> usr.getClass().getName())
                            .orElse("EMPTY"));
        }

        return optUserIdentity.orElseThrow(() -> {
            LOGGER.error("optUserIdentity should not be empty here. request: {}, attributeMap: {}",
                    request.getRequestURI(), attributeMap);
            return new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, attributeMap);
        });
    }

    private void processAttributes(final AttributeMap attributeMap,
                                   final Optional<UserIdentity> optUserIdentity) {
        if (attributeMap != null) {
            // Add the user identified in the token (if present) to the attribute map.
            // Use both ID and username as the ID will likely be a nasty UUID while the username will be more
            // useful for a human to read.
            optUserIdentity.ifPresent(userIdentity -> {
                NullSafe.consume(userIdentity.getId(), id ->
                        attributeMap.put(StandardHeaderArguments.UPLOAD_USER_ID, id));
                NullSafe.consume(userIdentity.getPreferredUsername(), username ->
                        attributeMap.put(StandardHeaderArguments.UPLOAD_USERNAME, username));
            });

            // Remove authorization header from attributes as it should not be stored or forwarded on.
            removeAuthorisationEntries(attributeMap);
        }
    }

    @Override
    public boolean hasAuthenticationToken(final HttpServletRequest request) {
        return userIdentityFactory.hasAuthenticationToken(request);
    }

    @Override
    public void removeAuthorisationEntries(final Map<String, String> headers) {
        NullSafe.consume(headers, userIdentityFactory::removeAuthEntries);
    }

    @Override
    public Map<String, String> getAuthHeaders(final UserIdentity userIdentity) {
        return userIdentityFactory.getAuthHeaders(userIdentity);
    }

    @Override
    public Map<String, String> getServiceUserAuthHeaders() {
        return userIdentityFactory.getServiceUserAuthHeaders();
    }
}
