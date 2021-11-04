package stroom.explorer.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

class ExplorerSessionImpl implements ExplorerSession {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExplorerSessionImpl.class);
    private static final String MIN_EXPLORER_TREE_MODEL_ID = "MIN_EXPLORER_TREE_MODEL_ID";

    private final Provider<HttpServletRequest> httpServletRequestProvider;

    @Inject
    public ExplorerSessionImpl(final Provider<HttpServletRequest> httpServletRequestProvider) {
        this.httpServletRequestProvider = httpServletRequestProvider;
    }

    @Override
    public Optional<Long> getMinExplorerTreeModelId() {
        try {
            return getSession().map(session -> {
                final Long minModelId = (Long) session.getAttribute(MIN_EXPLORER_TREE_MODEL_ID);

                LOGGER.debug(() ->
                        LogUtil.message("getMinExplorerTreeModelId - sessionId: {}, minModelId: {}",
                                session.getId(), minModelId));

                return minModelId;
            });
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public void setMinExplorerTreeModelId(final long minModelId) {
        try {
            getSession().ifPresent(session -> {
                LOGGER.debug(() ->
                        LogUtil.message(
                                "setMinExplorerTreeModelId - sessionId: {}, minModelId: {}",
                                session.getId(), minModelId));
                session.setAttribute(MIN_EXPLORER_TREE_MODEL_ID, minModelId);
            });
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
                    // Need to create the session if there isn't one as the explorer tree model update process
                    // relies on having a session available, which there may not be if auth is disabled.
                    final Optional<HttpSession> optSession = Optional.ofNullable(request.getSession(true));

                    LOGGER.debug(() -> "session id: " + optSession.map(HttpSession::getId).orElse("null"));

                    return optSession;
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return Optional.empty();
    }
}
