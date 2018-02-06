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

package stroom.explorer.server;

import org.springframework.stereotype.Component;
import stroom.explorer.shared.DocumentType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
class ExplorerActionHandlersImpl implements ExplorerActionHandlers {
    private final Map<String, ExplorerActionHandler> allHandlers = new ConcurrentHashMap<>();
    private final Map<String, DocumentType> allTypes = new ConcurrentHashMap<>();

    private final AtomicBoolean rebuild = new AtomicBoolean();
    private volatile List<DocumentType> documentTypes;

    @Override
    public void add(final boolean system, final int priority, final String type, final String displayType, final ExplorerActionHandler explorerActionHandler) {
        allHandlers.put(type, explorerActionHandler);

        final DocumentType documentType = new DocumentType(system, priority, type, displayType, getIconUrl(type));
        allTypes.put(type, documentType);

        rebuild.set(true);
    }

    private String getIconUrl(final String type) {
        return DocumentType.DOC_IMAGE_URL + type + ".svg";
    }

    List<DocumentType> getTypes() {
        if (rebuild.compareAndSet(true, false)) {
            final List<DocumentType> list = new ArrayList<>(allTypes.values());
            list.sort(Comparator.comparingInt(DocumentType::getPriority));
            this.documentTypes = list;
        }

        return documentTypes;
    }

    DocumentType getType(final String type) {
        return allTypes.get(type);
    }

    ExplorerActionHandler getHandler(final String type) {
        final ExplorerActionHandler explorerActionHandler = allHandlers.get(type);
        if (explorerActionHandler == null) {
            throw new RuntimeException("No handler can be found for '" + type + "'");
        }

        return explorerActionHandler;
    }
}