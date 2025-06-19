package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.security.api.UserIdentity;
import stroom.util.cert.CertificateExtractor;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public class CertificateAuthenticator implements AuthenticatorFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CertificateAuthenticator.class);

    private final CertificateExtractor certificateExtractor;

    @Inject
    public CertificateAuthenticator(final CertificateExtractor certificateExtractor) {
        this.certificateExtractor = certificateExtractor;
    }

    @Override
    public Optional<UserIdentity> authenticate(final HttpServletRequest request,
                                               final AttributeMap attributeMap) {

        Optional<UserIdentity> optUserIdentity = Optional.empty();

        try {
            final Optional<String> optCertCommonName = certificateExtractor.getCN(request);

            if (optCertCommonName.isPresent()) {
                // Not much we can do with the cert. The user won't exist in stroom, so as long
                // as the cert is trusted, we can get the CN and use that as the identity.
                // Debatable whether this identity should be created in the UserIdentityFactory
                optUserIdentity = optCertCommonName.map(CertificateUserIdentity::new);
            }

            LOGGER.debug("Returning optUserIdentity: {}", optUserIdentity);
            return optUserIdentity;
        } catch (final StroomStreamException e) {
            throw e;
        } catch (final Exception e) {
            throw new StroomStreamException(
                    StroomStatusCode.CLIENT_CERTIFICATE_NOT_AUTHENTICATED, attributeMap, e.getMessage());
        }
    }
}
