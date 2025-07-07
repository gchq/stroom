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

package stroom.importexport.impl;

import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.api.ImportExportActionHandlers;
import stroom.util.concurrent.LazyValue;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Singleton
class ImportExportActionHandlersImpl implements ImportExportActionHandlers {

    private final Provider<Set<ImportExportActionHandler>> importExportActionHandlerProviders;
    private final LazyValue<Map<String, ImportExportActionHandler>> lazyTypeToHandlerMap;

    @Inject
    ImportExportActionHandlersImpl(final Provider<Set<ImportExportActionHandler>> importExportActionHandlerProviders) {
        this.importExportActionHandlerProviders = importExportActionHandlerProviders;
        this.lazyTypeToHandlerMap = LazyValue.initialisedBy(this::createHandlers);
    }

    ImportExportActionHandler getHandler(final String type) {
        return getHandlers().get(type);
    }

    boolean isSingletonDoc(final String type) {
        Objects.requireNonNull(type);
        final ImportExportActionHandler handler = getHandlers().get(type);
        Objects.requireNonNull(handler, () ->
                "No ImportExportActionHandler found for type '" + type + "'");
        return handler.isSingleton();
    }

    @Override
    public Map<String, ImportExportActionHandler> getHandlers() {
        return lazyTypeToHandlerMap.getValueWithLocks();
    }

    private Map<String, ImportExportActionHandler> createHandlers() {
        final Map<String, ImportExportActionHandler> map = new HashMap<>();
        for (final ImportExportActionHandler handler : importExportActionHandlerProviders.get()) {
            final String type = handler.getType();
            final ImportExportActionHandler existingActionHandler = map.putIfAbsent(type, handler);
            if (existingActionHandler != null) {
                throw new RuntimeException("A handler already exists for '" + type + "' existing {" +
                                           existingActionHandler + "} new {" + handler + "}");
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
