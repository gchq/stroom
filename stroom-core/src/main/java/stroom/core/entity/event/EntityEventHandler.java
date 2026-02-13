/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.core.entity.event;

import stroom.security.api.SecurityContext;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEvent.Handler;
import stroom.util.entityevent.EntityEventBatch;
import stroom.util.entityevent.EntityEventKey;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Singleton
class EntityEventHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EntityEventHandler.class);
    private final Map<String, Map<EntityAction, List<Handler>>> typeToGroupedHandlersMap = new HashMap<>();
    private volatile boolean initialised;

    private final Provider<Set<Handler>> entityEventHandlerProvider;
    private final SecurityContext securityContext;

    @Inject
    EntityEventHandler(final Provider<Set<Handler>> entityEventHandlerProvider,
                       final SecurityContext securityContext) {
        this.entityEventHandlerProvider = entityEventHandlerProvider;
        this.securityContext = securityContext;
    }

    @NullMarked
    void fireLocally(final EntityEvent event) {
        // Ensure all incoming calls belong to authenticated users with administrative permissions.
        // Note that this should always be the processing user really as the EntityEventBus is responsible for
        // distributing entity events to all nodes and should be sending all requests as the processing user.
        if (!securityContext.isAdmin()) {
            LOGGER.error("Only an account with administrative privileges can fire an entity event, user: {}",
                    securityContext.getUserIdentity());
        } else {
            // Fire to type specific handlers.
            fireEventByType(event, event.getType());
            // Fire to any (*) type handlers.
            fireEventByType(event, EntityEvent.TYPE_WILDCARD);
        }
    }

    @NullMarked
    void fireLocally(final EntityEventBatch events) {
        Objects.requireNonNull(events);
        // Ensure all incoming calls belong to authenticated users with administrative permissions.
        // Note that this should always be the processing user really as the EntityEventBus is responsible for
        // distributing entity events to all nodes and should be sending all requests as the processing user.
        if (events.hasItems()) {
            if (!securityContext.isAdmin()) {
                LOGGER.error("Only an account with administrative privileges can fire entity events, user: {}",
                        securityContext.getUserIdentity());
            } else {
                final Map<EntityEventKey, EntityEventBatch> groupedBatches = events.groupedByKey();
                groupedBatches.forEach((entityEventKey, entityEventBatch) -> {
                    final Map<EntityAction, List<Handler>> actionToHandlersMap = getHandlersMap()
                            .get(entityEventKey.type());
                    final Map<EntityAction, List<Handler>> wildCardedActionToHandlersMap = getHandlersMap()
                            .get(EntityEvent.TYPE_WILDCARD);

                    entityEventBatch.forEach(entityEvent -> {
                        if (NullSafe.hasEntries(actionToHandlersMap)) {
                            // Fire to type specific handlers.
                            fireEventByType(actionToHandlersMap, entityEvent);
                        }
                        if (NullSafe.hasEntries(wildCardedActionToHandlersMap)) {
                            // Fire to any (*) type handlers.
                            fireEventByType(wildCardedActionToHandlersMap, entityEvent);
                        }
                    });
                });
            }
        }
    }

    /**
     * Check to see if we can find a handler for this event. If there isn't one
     * then there is no point in firing the event.
     */
    boolean handlerExists(final EntityEvent event) {
        return handlerExists(event.asEntityEventKey());
    }

    /**
     * Check to see if we can find a handler for this event. If there isn't one
     * then there is no point in firing the event.
     */
    boolean handlerExists(final EntityEventKey entityEventKey) {
        List<Handler> dest = null;

        // Make sure there are some handlers that care about this event.
        final Map<EntityAction, List<Handler>> actionToHandlersMap = getHandlersMap().get(entityEventKey.type());
        if (actionToHandlersMap != null) {
            // Try and get generic handlers for this type.
            dest = actionToHandlersMap.get(null);

            // If we can't get generic handlers then try and get action specific
            // handlers.
            if (dest == null) {
                dest = actionToHandlersMap.get(entityEventKey.action());
            }
        }
        return NullSafe.hasItems(dest);
    }

    private void fireEventByType(final EntityEvent event, final String type) {
        final Map<EntityAction, List<Handler>> actionToHandlersMap = getHandlersMap().get(type);
        if (NullSafe.hasEntries(actionToHandlersMap)) {
            fireEventByType(actionToHandlersMap, event);
        }
    }

    @NullMarked
    private void fireEventByType(final Map<EntityAction, List<Handler>> actionToHandlersMap,
                                 final EntityEvent event) {
        // Fire to global action handlers.
        fireEventByAction(actionToHandlersMap, event, null);
        // Fire to specific action handlers.
        fireEventByAction(actionToHandlersMap, event, event.getAction());
    }

    private void fireEventByAction(final Map<EntityAction, List<Handler>> map,
                                   final EntityEvent event,
                                   final EntityAction action) {
        final List<Handler> handlers = map.get(action);
        if (handlers != null) {
            for (final Handler handler : handlers) {
                try {
                    LOGGER.trace(() -> LogUtil.message("fireEventByAction() - action: {}, handler: {}, event: {}",
                            action, handler.getClass().getSimpleName(), event));
                    handler.onChange(event);
                } catch (final RuntimeException e) {
                    LOGGER.error("Unable to handle onChange event, handler: {}",
                            LogUtil.typedValue(handler), e);
                }
            }
        }
    }

    private void addHandler(final Handler handler, final String type, final EntityAction... actions) {
        // Can't use an EnumMap as we may have null keys
        final Map<EntityAction, List<Handler>> map = typeToGroupedHandlersMap.computeIfAbsent(
                type, ignored -> new HashMap<>());
        LOGGER.info("Adding event handler {} for type '{}' and actions: {}",
                handler.getClass().getSimpleName(),
                type,
                actions);
        if (NullSafe.isEmptyArray(actions)) {
            addHandlerForAction(map, handler, null);
        } else {
            final List<EntityAction> sortedActions = Arrays.stream(actions)
                    .sorted()
                    .toList();
            for (final EntityAction action : sortedActions) {
                addHandlerForAction(map, handler, action);
            }
        }
    }

    private void addHandlerForAction(final Map<EntityAction, List<Handler>> actionToHandlersMap,
                                     final Handler handler,
                                     final EntityAction action) {
        actionToHandlersMap.computeIfAbsent(action, k -> new ArrayList<>())
                .add(handler);
    }

    private Map<String, Map<EntityAction, List<Handler>>> getHandlersMap() {
        if (!initialised) {
            initialise();
        }
        return typeToGroupedHandlersMap;
    }

    private synchronized void initialise() {
        if (!initialised) {
            LOGGER.info("initialise()");
            try {
                final List<Handler> handlers = NullSafe.stream(entityEventHandlerProvider.get())
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(handler -> handler.getClass().getSimpleName()))
                        .toList();
                for (final Handler handler : handlers) {
                    final stroom.util.entityevent.EntityEventHandler[] annotations = handler.getClass()
                            .getAnnotationsByType(stroom.util.entityevent.EntityEventHandler.class);
                    if (annotations.length > 0) {
                        for (final stroom.util.entityevent.EntityEventHandler annotation : annotations) {
                            final String type = annotation.type();
                            final EntityAction[] actions = annotation.action();
                            addHandler(handler, type, actions);
                        }
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
