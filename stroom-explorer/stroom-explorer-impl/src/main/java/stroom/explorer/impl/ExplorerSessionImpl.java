package stroom.explorer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Optional;

class ExplorerSessionImpl implements ExplorerSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExplorerSessionImpl.class);
    private static final String MIN_EXPLORER_TREE_MODEL_BUILD_TIME = "MIN_EXPLORER_TREE_MODEL_BUILD_TIME";

    private final Provider<HttpServletRequest> httpServletRequestProvider;

    @Inject
    public ExplorerSessionImpl(final Provider<HttpServletRequest> httpServletRequestProvider) {
        this.httpServletRequestProvider = httpServletRequestProvider;
    }

    @Override
    public Optional<Long> getMinExplorerTreeModelBuildTime() {
        final HttpServletRequest request = getRequest();
        if (request == null) {
            LOGGER.debug("Request holder has no current request");
            return Optional.empty();
        }

        final HttpSession session = request.getSession();
        final Object object = session.getAttribute(MIN_EXPLORER_TREE_MODEL_BUILD_TIME);
        return Optional.ofNullable((Long) object);
    }

    @Override
    public void setMinExplorerTreeModelBuildTime(final long buildTime) {
        final HttpServletRequest request = getRequest();
        if (request == null) {
            LOGGER.debug("Request holder has no current request");

        } else {
            final HttpSession session = request.getSession();
            session.setAttribute(MIN_EXPLORER_TREE_MODEL_BUILD_TIME, buildTime);
        }
    }

    private HttpServletRequest getRequest() {
        if (httpServletRequestProvider != null) {
            try {
                return httpServletRequestProvider.get();
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }
        return null;
    }
}
