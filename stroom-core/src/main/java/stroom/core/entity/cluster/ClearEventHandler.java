package stroom.core.entity.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.SecurityContext;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;

@Singleton
public class ClearEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClearEventHandler.class);

    private final SecurityContext securityContext;
    private final Set<Clearable> clearables;

    @Inject
    ClearEventHandler(final SecurityContext securityContext,
                             final Set<Clearable> clearables) {
        this.securityContext = securityContext;
        this.clearables = clearables;
    }

    void clearLocally() {
        securityContext.secure(() -> clearables.forEach(clearable -> {
            LOGGER.info("Calling clear on {}", clearable);
            clearable.clear();
        }));
    }
}
