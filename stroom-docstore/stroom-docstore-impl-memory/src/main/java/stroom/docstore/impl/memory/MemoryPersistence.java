/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.docstore.impl.memory;

import stroom.docref.DocRef;
import stroom.docstore.api.RWLockFactory;
import stroom.docstore.impl.Persistence;
import stroom.util.shared.Clearable;

import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class MemoryPersistence implements Persistence, Clearable {

    private static final RWLockFactory LOCK_FACTORY = new NoLockFactory();

    private final Map<DocRef, Map<String, byte[]>> map = new ConcurrentHashMap<>();

    @Override
    public boolean exists(final DocRef docRef) {
        return map.containsKey(docRef);
    }

    @Override
    public Map<String, byte[]> read(final DocRef docRef) {
        return map.get(docRef);
    }

    @Override
    public void write(final DocRef docRef, final boolean update, final Map<String, byte[]> data) {
        if (update) {
            if (!map.containsKey(docRef)) {
                throw new RuntimeException("Document does not exist with uuid=" + docRef.getUuid());
            }
        } else if (map.containsKey(docRef)) {
            throw new RuntimeException("Document already exists with uuid=" + docRef.getUuid());
        }

        map.put(docRef, data);
    }

    @Override
    public void delete(final DocRef docRef) {
        map.remove(docRef);
    }

    @Override
    public List<DocRef> list(final String type) {
        return map.keySet()
                .stream()
                .filter(docRef -> docRef.getType().equals(type))
                .collect(Collectors.toList());
    }

    @Override
    public RWLockFactory getLockFactory() {
        return LOCK_FACTORY;
    }

    @Override
    public void clear() {
        map.clear();
    }
}
