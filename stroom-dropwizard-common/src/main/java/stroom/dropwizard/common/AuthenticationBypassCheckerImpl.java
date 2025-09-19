package stroom.dropwizard.common;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AuthenticationBypassChecker;
import stroom.util.shared.NullSafe;

import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Set;

@Singleton
public class AuthenticationBypassCheckerImpl implements AuthenticationBypassChecker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AuthenticationBypassCheckerImpl.class);
    // Servlet name hard coded in io.dropwizard.core.server.AbstractServerFactory, so this
    // should match that.
    private static final String JERSEY_SERVLET_NAME = "jersey";

    // These are populated by a single thread when dropwizard boots, so no need for synch
    private final Set<String> unauthenticatedServletNames = new HashSet<>();
    private final Set<String> unauthenticatedApiPaths = new HashSet<>();

    void registerUnauthenticatedServletName(final String servletName) {
        LOGGER.debug("Registering unauthenticated servlet: {}", servletName);
        NullSafe.consume(servletName, unauthenticatedServletNames::add);
    }

    void registerUnauthenticatedApiPath(final String path) {
        LOGGER.debug("Registering unauthenticated API path: {}", path);
        NullSafe.consume(path, unauthenticatedApiPaths::add);
    }

    @Override
    public boolean isUnauthenticated(final String servletName, final String servletPath, final String fullPath) {
        if (servletPath == null) {
            return false;
        } else {
            final boolean canBypassAuth;
            // All rest resources live under the "jersey" servlet as named by dropwizard
            if (JERSEY_SERVLET_NAME.equals(servletName)) {
                // A REST API call
                // Note: because we are doing an equality check on the full path, we can't use
                // path params with unauthenticated rest methods
                canBypassAuth = unauthenticatedApiPaths.contains(fullPath);
            } else {
                // A servlet
                canBypassAuth = unauthenticatedServletNames.contains(servletName);
            }

            LOGGER.debug("servletName: {}, servletPath: {}, canBypassAuth: {}",
                    servletName, servletPath, canBypassAuth);
            return canBypassAuth;
        }
    }
}
