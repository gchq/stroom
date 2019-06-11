package stroom.explorer.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.servlet.HttpServletRequestHolder;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Optional;

@Component
class ExplorerSessionImpl implements ExplorerSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExplorerSessionImpl.class);
    private static final String MIN_EXPLORER_TREE_MODEL_BUILD_TIME = "MIN_EXPLORER_TREE_MODEL_BUILD_TIME";

    private final Provider<HttpServletRequestHolder> httpServletRequestHolderProvider;

    @Inject
    ExplorerSessionImpl(final Provider<HttpServletRequestHolder> httpServletRequestHolderProvider) {
        this.httpServletRequestHolderProvider = httpServletRequestHolderProvider;
    }

    @Override
    public Optional<Long> getMinExplorerTreeModelBuildTime() {
        final HttpServletRequestHolder httpServletRequestHolder = httpServletRequestHolderProvider.get();
        if (httpServletRequestHolder == null) {
            return Optional.empty();
        }

        final HttpServletRequest request = httpServletRequestHolder.get();
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
        final HttpServletRequestHolder httpServletRequestHolder = httpServletRequestHolderProvider.get();
        if (httpServletRequestHolder != null) {
            final HttpServletRequest request = httpServletRequestHolder.get();
            if (request == null) {
                LOGGER.debug("Request holder has no current request");

            } else {
                final HttpSession session = request.getSession();
                session.setAttribute(MIN_EXPLORER_TREE_MODEL_BUILD_TIME, buildTime);
            }
        }
    }
}
