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
        if (beanFactory != null) {
            try {
                final HttpServletRequestHolder holder = beanFactory.getBean(HttpServletRequestHolder.class);
                if (holder != null) {
                    return holder.get();
                }
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }
        return null;
    }

    @Override
    public void setBeanFactory(final BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
