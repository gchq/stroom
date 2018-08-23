/*
 * Copyright 2016 Crown Copyright
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

package stroom.entity.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.event.EntityEvent.Handler;
import stroom.entity.shared.EntityAction;
import stroom.task.TaskManager;
import stroom.util.lifecycle.StroomStartup;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
class EntityEventBusImpl implements EntityEventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityEventBusImpl.class);
    private final Map<String, Map<EntityAction, List<Handler>>> handlers = new HashMap<>();
    private volatile boolean initialised;

    private final Provider<Set<Handler>> entityEventHandlerProvider;
    private final TaskManager taskManager;

    private volatile boolean started = false;

    @Inject
    EntityEventBusImpl(final Provider<Set<Handler>> entityEventHandlerProvider, final TaskManager taskManager) {
        this.entityEventHandlerProvider = entityEventHandlerProvider;
        this.taskManager = taskManager;
    }

    @StroomStartup
    public void init() {
        started = true;
    }

    @Override
    public void fire(final EntityEvent event) {
        if (started) {
            fireGlobally(event);
        }
    }

    /**
     * Fires the entity change to all nodes in the cluster.
     */
    private void fireGlobally(final EntityEvent event) {
        // Make sure there are some handlers that care about this event.
        boolean handlerExists = handlerExists(event, event.getDocRef().getType());
        if (!handlerExists) {
            // Look for handlers that cater for all types.
            handlerExists = handlerExists(event, "*");
        }

        // If there are registered handlers then go ahead and fire the event.
        if (handlerExists) {
            // Force a local update so that changes are immediately reflected
            // for the current user.
            fireLocally(event);

            if (started) {
                // Dispatch the entity event to all nodes in the cluster.
                taskManager.execAsync(new DispatchEntityEventTask(event));
            }
        }
    }

    void fireLocally(final EntityEvent event) {
        // Fire to type specific handlers.
        fireEventByType(event, event.getDocRef().getType());
        // Fire to any (*) type handlers.
        fireEventByType(event, "*");
    }

    /**
     * Check to see if we can find a handler for this event. If there isn't one
     * then there is no point in firing the event.
     */
    private boolean handlerExists(final EntityEvent event, final String type) {
        List<Handler> dest = null;

        // Make sure there are some handlers that care about this event.
        final Map<EntityAction, List<Handler>> map = getHandlers().get(type);
        if (map != null) {
            // Try and get generic handlers for this type.
            dest = map.get(null);

            // If we can't get generic handlers then try and get action specific
            // handlers.
            if (dest == null) {
                dest = map.get(event.getAction());
            }
        }

        return dest != null && dest.size() > 0;
    }

    private void fireEventByType(final EntityEvent event, final String type) {
        final Map<EntityAction, List<Handler>> map = getHandlers().get(type);
        if (map != null) {
            // Fire to global action handlers.
            fireEventByAction(map, event, null);

            // Fire to specific action handlers.
            fireEventByAction(map, event, event.getAction());
        }
    }

    private void fireEventByAction(final Map<EntityAction, List<Handler>> map, final EntityEvent event,
                                   final EntityAction action) {
        final List<Handler> list = map.get(action);
        if (list != null) {
            for (final Handler handler : list) {
                try {
                    handler.onChange(event);
                } catch (final RuntimeException e) {
                    LOGGER.error("Unable to handle onChange event!", e);
                }
            }
        }
    }

    @Override
    public void addHandler(final Handler handler, final String type, final EntityAction... action) {
        final Map<EntityAction, List<Handler>> map = handlers.computeIfAbsent(type, k -> new HashMap<>());
        if (action == null || action.length == 0) {
            addHandlerForAction(map, handler, null);
        } else {
            for (final EntityAction act : action) {
                addHandlerForAction(map, handler, act);
            }
        }
    }

    private void addHandlerForAction(final Map<EntityAction, List<Handler>> map,
                                     final Handler handler,
                                     final EntityAction action) {
        map.computeIfAbsent(action, k -> new ArrayList<>()).add(handler);
    }

    private Map<String, Map<EntityAction, List<Handler>>> getHandlers() {
        if (!initialised) {
            initialise();
        }

        return handlers;
    }

    private synchronized void initialise() {
        if (!initialised) {
            try {
                for (final Handler handler : entityEventHandlerProvider.get()) {
                    final EntityEventHandler annotation = handler.getClass().getAnnotation(EntityEventHandler.class);
                    if (annotation != null) {
                        final String type = annotation.type();
                        addHandler(handler, type, annotation.action());
                    } else {
                        LOGGER.error("Annotation not found");
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to initialise EntityEventBusImpl!", e);
            }

            initialised = true;
        }
    }
}
