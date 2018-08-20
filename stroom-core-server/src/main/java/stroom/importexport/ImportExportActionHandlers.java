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

import stroom.guice.StroomBeanStore;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
class ImportExportActionHandlers {
    private final StroomBeanStore beanStore;
    private final ImportExportActionHandlerFactory importExportActionHandlerFactory;

    private volatile Handlers handlers;

    @Inject
    ImportExportActionHandlers(final StroomBeanStore beanStore, final ImportExportActionHandlerFactory importExportActionHandlerFactory) {
        this.beanStore = beanStore;
        this.importExportActionHandlerFactory = importExportActionHandlerFactory;
    }

    ImportExportActionHandler getHandler(final String type) {
        final Map<String, ImportExportActionHandler> handlerMap = getAllHandlers();
        return handlerMap.get(type);
    }

    Map<String, ImportExportActionHandler> getAllHandlers() {
        return getHandlers().allHandlers;
    }

    private Handlers getHandlers() {
        if (handlers == null) {
            handlers = new Handlers(beanStore, importExportActionHandlerFactory);
        }
        return handlers;
    }

    private static class Handlers {
        private final Map<String, ImportExportActionHandler> allHandlers = new ConcurrentHashMap<>();

        Handlers(final StroomBeanStore beanStore,
                 final ImportExportActionHandlerFactory importExportActionHandlerFactory) {
            // Add external handlers.
            final Set<ImportExportActionHandler> importExportActionHandlers = importExportActionHandlerFactory.getImportExportActionHandlers();
            if (importExportActionHandlers != null) {
                importExportActionHandlers.forEach(this::addImportExportActionHandler);
            }

            // Add internal handlers.
            final Set<ImportExportActionHandler> set = beanStore.getInstancesOfType(ImportExportActionHandler.class);
            set.forEach(this::addImportExportActionHandler);
        }

        private void addImportExportActionHandler(final ImportExportActionHandler handler) {
            final String type = handler.getDocumentType().getType();

            final ImportExportActionHandler existingActionHandler = allHandlers.putIfAbsent(type, handler);
            if (existingActionHandler != null) {
                throw new RuntimeException("A handler already exists for '" + type + "' existing {" + existingActionHandler + "} new {" + handler + "}");
            }
        }
    }
}