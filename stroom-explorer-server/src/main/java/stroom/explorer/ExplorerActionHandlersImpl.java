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

package stroom.explorer;

import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.util.spring.StroomBeanStore;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class ExplorerActionHandlers {
    private final StroomBeanStore beanStore;

    private volatile Handlers handlers;

    @Inject
    ExplorerActionHandlers(final StroomBeanStore beanStore) {
        this.beanStore = beanStore;
    }

    List<DocumentType> getNonSystemTypes() {
        return getHandlers().documentTypes;
    }

    DocumentType getType(final String type) {
        return getHandlers().allTypes.get(type);
    }

    ExplorerActionHandler getHandler(final String type) {
        final ExplorerActionHandler explorerActionHandler = getHandlers().allHandlers.get(type);
        if (explorerActionHandler == null) {
            throw new RuntimeException("No handler can be found for '" + type + "'");
        }

        return explorerActionHandler;
    }

    private Handlers getHandlers() {
        if (handlers == null) {
            handlers = new Handlers(beanStore);
        }
        return handlers;
    }

    private static class Handlers {
        private final Map<String, ExplorerActionHandler> allHandlers = new ConcurrentHashMap<>();
        private final Map<String, DocumentType> allTypes = new ConcurrentHashMap<>();
        private final List<DocumentType> documentTypes;

        Handlers(final StroomBeanStore beanStore) {
            final Set<String> set = beanStore.getStroomBeanByType(ExplorerActionHandler.class);
            set.forEach(name -> {
                if (!name.toLowerCase().contains("cache")) {
                    final Object object = beanStore.getBean(name);
                    if (object != null && object instanceof ExplorerActionHandler) {
                        final ExplorerActionHandler explorerActionHandler = (ExplorerActionHandler) object;
                        final String type = explorerActionHandler.getDocumentType().getType();

                        final ExplorerActionHandler existingActionHandler = allHandlers.putIfAbsent(type, explorerActionHandler);
                        if (existingActionHandler != null) {
                            throw new RuntimeException("A handler already exists for '" + type + "' existing {" + existingActionHandler + "} new {" + explorerActionHandler + "}");
                        }

                        final DocumentType existingDocumentType = allTypes.putIfAbsent(type, explorerActionHandler.getDocumentType());
                        if (existingDocumentType != null) {
                            throw new RuntimeException("A document type already exists for '" + type + "' existing {" + existingDocumentType + "} new {" + explorerActionHandler.getDocumentType() + "}");
                        }
                    }
                }
            });

            final List<DocumentType> list = new ArrayList<>(allTypes.values().stream()
                    .filter(type -> !DocumentTypes.isSystem(type.getType()))
                    .sorted(Comparator.comparingInt(DocumentType::getPriority))
                    .collect(Collectors.toList()));
            this.documentTypes = list;
        }
    }
}