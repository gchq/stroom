package stroom.explorer.server;

import org.springframework.stereotype.Component;
import stroom.servlet.HttpServletRequestHolder;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Optional;

@Component
class ExplorerSessionImpl implements ExplorerSession {
    private static final String MIN_EXPLORER_TREE_MODEL_BUILD_TIME = "MIN_EXPLORER_TREE_MODEL_BUILD_TIME";

    private final HttpServletRequestHolder httpServletRequestHolder;

    @Inject
    ExplorerSessionImpl(final HttpServletRequestHolder httpServletRequestHolder) {
        this.httpServletRequestHolder = httpServletRequestHolder;
    }

    @Override
    public Optional<Long> getMinExplorerTreeModelBuildTime() {
        final HttpSession session = getSession();
        final Object object = session.getAttribute(MIN_EXPLORER_TREE_MODEL_BUILD_TIME);
        return Optional.ofNullable((Long) object);
    }

    @Override
    public void setMinExplorerTreeModelBuildTime(final long buildTime) {
        getSession().setAttribute(MIN_EXPLORER_TREE_MODEL_BUILD_TIME, buildTime);
    }

    private HttpSession getSession() {
        final HttpServletRequest request = httpServletRequestHolder.get();
        if (request == null) {
            throw new NullPointerException("Request holder has no current request");
        }
        return request.getSession();
    }
}
