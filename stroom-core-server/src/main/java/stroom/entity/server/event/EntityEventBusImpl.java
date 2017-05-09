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

package stroom.entity.server.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.entity.shared.EntityAction;
import stroom.task.server.TaskManager;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomStartup;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EntityEventBusImpl implements EntityEventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityEventBusImpl.class);
    private final Map<String, Map<EntityAction, List<EntityEvent.Handler>>> handlers = new HashMap<>();
    private volatile boolean initialised;

    @Resource
    private StroomBeanStore stroomBeanStore;
    @Resource
    private TaskManager taskManager;

    private volatile boolean started = false;

    @StroomStartup
    public void init() {
        started = true;
    }

    @Override
    public void fire(final EntityEvent event) {
        fireGlobally(event);
    }

    /**
     * Fires the entity change to all nodes in the cluster.
     */
    public void fireGlobally(final EntityEvent event) {
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

    public void fireLocally(final EntityEvent event) {
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
        List<EntityEvent.Handler> dest = null;

        // Make sure there are some handlers that care about this event.
        final Map<EntityAction, List<EntityEvent.Handler>> map = getHandlers().get(type);
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
        final Map<EntityAction, List<EntityEvent.Handler>> map = getHandlers().get(type);
        if (map != null) {
            // Fire to global action handlers.
            fireEventByAction(map, event, null);

            // Fire to specific action handlers.
            fireEventByAction(map, event, event.getAction());
        }
    }

    private void fireEventByAction(final Map<EntityAction, List<EntityEvent.Handler>> map, final EntityEvent event,
                                   final EntityAction action) {
        final List<EntityEvent.Handler> list = map.get(action);
        if (list != null) {
            for (final EntityEvent.Handler handler : list) {
                try {
                    handler.onChange(event);
                } catch (final Exception e) {
                    LOGGER.error("Unable to handle onChange event!", e);
                }
            }
        }
    }

    @Override
    public void addHandler(final EntityEvent.Handler handler, final String type, final EntityAction... action)
            throws Exception {
        Map<EntityAction, List<EntityEvent.Handler>> map = handlers.get(type);
        if (map == null) {
            map = new HashMap<>();
            handlers.put(type, map);
        }

        if (action == null || action.length == 0) {
            addHandlerForAction(map, handler, null);
        } else {
            for (final EntityAction act : action) {
                addHandlerForAction(map, handler, act);
            }
        }
    }

    private void addHandlerForAction(final Map<EntityAction, List<EntityEvent.Handler>> map,
                                     final EntityEvent.Handler handler, final EntityAction action) throws Exception {
        List<EntityEvent.Handler> list = map.get(action);
        if (list == null) {
            list = new ArrayList<>();
            map.put(action, list);
        }
        list.add(handler);
    }

    private Map<String, Map<EntityAction, List<EntityEvent.Handler>>> getHandlers() {
        if (!initialised) {
            initialise();
        }

        return handlers;
    }

    private synchronized void initialise() {
        if (!initialised) {
            try {
                for (final String bean : stroomBeanStore.getStroomBean(EntityEventHandler.class)) {
                    final EntityEventHandler annotation = stroomBeanStore.findAnnotationOnBean(bean,
                            EntityEventHandler.class);
                    if (annotation != null) {
                        final Object instance = stroomBeanStore.getBean(bean);

                        if (!(instance instanceof EntityEvent.Handler)) {
                            throw new RuntimeException("Unexpected type for entity handler: " + bean);
                        }

                        final EntityEvent.Handler handler = (EntityEvent.Handler) instance;
                        final String type = annotation.type();
                        if (type == null) {
                            throw new RuntimeException("Null type");
                        }

                        addHandler(handler, type, annotation.action());
                    } else {
                        LOGGER.error("Annotation not found");
                    }
                }
            } catch (final Exception e) {
                LOGGER.error("Unable to initialise EntityEventBusImpl!", e);
            }

            initialised = true;
        }
    }
}
