package stroom.explorer.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.stereotype.Component;
import stroom.servlet.HttpServletRequestHolder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Optional;

@Component
class ExplorerSessionImpl implements ExplorerSession, BeanFactoryAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExplorerSessionImpl.class);
    private static final String MIN_EXPLORER_TREE_MODEL_BUILD_TIME = "MIN_EXPLORER_TREE_MODEL_BUILD_TIME";

    private BeanFactory beanFactory;

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
            if (beanFactory != null) {
                final HttpServletRequestHolder holder = beanFactory.getBean(HttpServletRequestHolder.class);
                if (holder != null) {
                    final HttpServletRequest request = holder.get();
                    if (request == null) {
                        LOGGER.debug("Request holder has no current request");
                    } else {
                        return Optional.ofNullable(request.getSession(false));
                    }
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public void setBeanFactory(final BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
