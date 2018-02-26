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

import stroom.util.spring.StroomBeanStore;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class ImportExportActionHandlers {
    private final StroomBeanStore beanStore;

    private volatile Map<String, ImportExportActionHandler> handlers;

    @Inject
    ImportExportActionHandlers(final StroomBeanStore beanStore) {
        this.beanStore = beanStore;
    }

    ImportExportActionHandler getHandler(final String type) {
        final Map<String, ImportExportActionHandler> handlerMap = getAllHandlers();
        return handlerMap.get(type);
    }

    Map<String, ImportExportActionHandler> getAllHandlers() {
        if (handlers == null) {
            handlers = findHandlers();
        }
        return handlers;
    }

    private Map<String, ImportExportActionHandler> findHandlers() {
        final Map<String, ImportExportActionHandler> handlerMap = new HashMap<>();
        final Set<String> set = beanStore.getStroomBeanByType(ImportExportActionHandler.class);
        set.forEach(name -> {
            final Object object = beanStore.getBean(name);
            if (object != null && object instanceof ImportExportActionHandler) {
                final ImportExportActionHandler importExportActionHandler = (ImportExportActionHandler) object;
                handlerMap.put(importExportActionHandler.getDocumentType().getType(), importExportActionHandler);
            }
        });
        return handlerMap;
    }
}