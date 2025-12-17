/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.impl.event;

import stroom.security.api.SecurityContext;
import stroom.security.impl.event.PermissionChangeEvent.Handler;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

@Singleton
class PermissionChangeEventHandlers {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionChangeEventHandlers.class);
    private final Set<Handler> handlers = new HashSet<>();
    private volatile boolean initialised;

    private final Provider<Set<Handler>> handlerProvider;
    private final SecurityContext securityContext;

    @Inject
    PermissionChangeEventHandlers(final Provider<Set<Handler>> handlerProvider,
                                  final SecurityContext securityContext) {
        this.handlerProvider = handlerProvider;
        this.securityContext = securityContext;
    }

    public void fireLocally(final PermissionChangeEvent event) {
        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserRef(),
                    "Only the processing user can fire permission change events");
        }

        try {
            final Set<Handler> set = getHandlers();
            for (final Handler handler : set) {
                try {
                    handler.onChange(event);
                } catch (final Exception e) {
                    LOGGER.error("Unable to handle onChange request!", e);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public void addHandler(final Handler handler) {
        handlers.add(handler);
    }

    private Set<Handler> getHandlers() {
        if (!initialised) {
            initialise();
        }

        return handlers;
    }

    private synchronized void initialise() {
        if (!initialised) {
            try {
                for (final Handler handler : handlerProvider.get()) {
                    addHandler(handler);
                }
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to initialise EntityEventBusImpl!", e);
            }

            initialised = true;
        }
    }
}
