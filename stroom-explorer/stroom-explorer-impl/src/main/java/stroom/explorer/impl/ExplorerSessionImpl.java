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
        try {
            return getSession().map(session -> (Long) session.getAttribute(MIN_EXPLORER_TREE_MODEL_BUILD_TIME));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public void setMinExplorerTreeModelBuildTime(final long buildTime) {
        try {
            getSession().ifPresent(session -> session.setAttribute(MIN_EXPLORER_TREE_MODEL_BUILD_TIME, buildTime));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
    }

    private Optional<HttpSession> getSession() {
        try {
            if (httpServletRequestProvider != null) {
                final HttpServletRequest request = httpServletRequestProvider.get();
                if (request == null) {
                    LOGGER.debug("Request provider has no current request");
                } else {
                    return Optional.ofNullable(request.getSession());
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return Optional.empty();
    }
}
