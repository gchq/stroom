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

package stroom.document.client;

import stroom.docref.DocRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DocumentTabManager {

    private final Map<DocRef, List<DocumentTabData>> documentToTabDataMap = new HashMap<>();

    public List<DocumentTabData> get(final DocRef docRef) {
        return documentToTabDataMap.getOrDefault(docRef, Collections.emptyList());
    }

    public void put(final DocRef docRef, final DocumentTabData documentTabData) {
        if (docRef == null) {
            throw new IllegalArgumentException("DocRef cannot be null");
        }

        documentToTabDataMap
                .computeIfAbsent(docRef, k -> new ArrayList<>())
                .add(documentTabData);
    }

    public boolean remove(final DocRef docRef, final DocumentTabData tabData) {
        final List<DocumentTabData> list = documentToTabDataMap.get(docRef);
        if (list == null) {
            return false;
        }

        final boolean removed = list.remove(tabData);

        // Clean up empty lists
        if (list.isEmpty()) {
            documentToTabDataMap.remove(docRef);
        }

        return removed;
    }

    public List<DocumentTabData> getAll() {
        return documentToTabDataMap.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
}
