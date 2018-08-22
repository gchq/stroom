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

package stroom.importexport;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Singleton
class ImportExportActionHandlers {
    private final Provider<Set<ImportExportActionHandler>> importExportActionHandlerProviders;
    private volatile Map<String, ImportExportActionHandler> handlers;

    @Inject
    ImportExportActionHandlers(final Provider<Set<ImportExportActionHandler>> importExportActionHandlerProviders) {
        this.importExportActionHandlerProviders = importExportActionHandlerProviders;
    }

    ImportExportActionHandler getHandler(final String type) {
        return getHandlers().get(type);
    }

    Map<String, ImportExportActionHandler> getHandlers() {
        if (handlers == null) {
            final Map<String, ImportExportActionHandler> map = new HashMap<>();
            for (ImportExportActionHandler handler : importExportActionHandlerProviders.get()) {
                final String type = handler.getDocumentType().getType();

                final ImportExportActionHandler existingActionHandler = map.putIfAbsent(type, handler);
                if (existingActionHandler != null) {
                    throw new RuntimeException("A handler already exists for '" + type + "' existing {" + existingActionHandler + "} new {" + handler + "}");
                }
            }
            handlers = map;
        }

        return handlers;
    }
}