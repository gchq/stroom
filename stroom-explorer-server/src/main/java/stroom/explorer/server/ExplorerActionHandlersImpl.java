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

import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ExplorerActionHandlersImpl implements ExplorerActionHandlers {
    private final Map<String, Provider<?>> allHandlers = new ConcurrentHashMap<>();
    private final Map<String, DocumentType> allTypes = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> allTags = new ConcurrentHashMap<>();

    private final AtomicBoolean rebuild = new AtomicBoolean();
    private volatile List<DocumentType> documentTypes;

    @Override
    public <T extends ExplorerActionHandler> void add(final int priority, final String type, final String displayType, final Provider<T> provider, final String... tags) {
        allHandlers.put(type, provider);

        final DocumentType documentType = new DocumentType(priority, type, displayType, getIconUrl(type));
        allTypes.put(type, documentType);

        if (tags == null) {
            allTags.put(type, Collections.emptySet());
        } else {
            allTags.put(type, new HashSet<>(Arrays.asList(tags)));
        }

        rebuild.set(true);
    }

    private String getIconUrl(final String type) {
        return DocumentType.DOC_IMAGE_URL + type + ".png";
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
        final Provider<?> provider = allHandlers.get(type);
        if (provider == null) {
            throw new RuntimeException("No handler can be found for '" + type + "'");
        }

        final Object object = provider.get();
        return (ExplorerActionHandler) object;
    }

    Set<String> getTags(final String type) {
        return allTags.get(type);
    }
}