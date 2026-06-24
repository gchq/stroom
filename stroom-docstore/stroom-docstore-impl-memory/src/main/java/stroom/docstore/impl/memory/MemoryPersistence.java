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
import stroom.docstore.impl.GenericDoc;
import stroom.docstore.impl.Persistence;
import stroom.docstore.shared.AuditAction;
import stroom.docstore.shared.DocAuditEntry;
import stroom.docstore.shared.DocAuditUser;
import stroom.importexport.api.ImportExportDocument;
import stroom.util.PredicateUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.string.PatternUtil;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
public class MemoryPersistence implements Persistence, Clearable {

    private static final RWLockFactory LOCK_FACTORY = new NoLockFactory();
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MemoryPersistence.class);

    private final Map<DocRef, ImportExportDocument> map = new ConcurrentHashMap<>();

    @Override
    public boolean exists(final DocRef docRef) {
        return map.containsKey(docRef);
    }

    @Override
    public ImportExportDocument read(final DocRef docRef) {
        return map.get(docRef);
    }

    @Override
    public void write(final DocRef docRef,
                      final AuditAction auditAction,
                      final UserRef userRef,
                      final ImportExportDocument importExportDocument) {
        if (auditAction.isUpdate()) {
            if (!map.containsKey(docRef)) {
                throw new RuntimeException("Document does not exist with uuid=" + docRef.getUuid());
            }
        } else if (auditAction.isCreate() && map.containsKey(docRef)) {
            throw new RuntimeException("Document already exists with uuid=" + docRef.getUuid());
        }

        map.put(docRef, importExportDocument);
    }

    @Override
    public void delete(final DocRef docRef, final UserRef userRef) {
        map.remove(docRef);
    }

    @Override
    public List<DocRef> list(final Collection<String> types) {
        if (NullSafe.isEmptyCollection(types)) {
            return Collections.emptyList();
        }
        return map.keySet()
                .stream()
                .filter(docRef -> types.contains(docRef.getType()))
                .collect(Collectors.toList());
    }

    @Override
    public List<DocRef> list(final String type) {
        return map.keySet()
                .stream()
                .filter(docRef -> docRef.getType().equals(type))
                .collect(Collectors.toList());
    }

//    @Override
//    public boolean exists(final String uuid) {
//        return map.keySet()
//                .stream()
//                .anyMatch(docRef -> docRef.getUuid().equals(uuid));
//    }

    @Override
    public RWLockFactory getLockFactory() {
        return LOCK_FACTORY;
    }

    @Override
    public List<DocRef> findDocRefsEmbeddedIn(final DocRef parent) {
        return List.of();
    }

    @Override
    public void clear() {
        map.clear();
    }

    /**
     * Find docRefs by name and type. Name can be optionally wild carded using '*' to match 0-many chars.
     */
    @Override
    public List<DocRef> find(final String type,
                             final String nameFilter,
                             final boolean allowWildCards) {
        // Default impl that does all filtering in java. Not efficient for DB impls.
        return nameFilter == null
                ? Collections.emptyList()
                : find(type, List.of(nameFilter), allowWildCards);
    }

    /**
     * Find docRefs by type and one or more nameFilters.
     * nameFilters can be optionally wild carded using '*' to match 0-many chars.
     */
    @Override
    public List<DocRef> find(final String type,
                             final List<String> nameFilters,
                             final boolean allowWildCards) {
        return find(List.of(type), nameFilters, allowWildCards);
    }

    /**
     * Find docRefs by name across multiple types. If types is null or empty, searches ALL types.
     * This is the cross-type variant used by caches and services.
     */
    @Override
    public List<DocRef> find(final Collection<String> types,
                             final List<String> nameFilters,
                             final boolean allowWildCards) {
        // Default impl that does all filtering in java. Not efficient for DB impls.
        if (NullSafe.isEmptyCollection(nameFilters)) {
            return Collections.emptyList();
        } else {
            // Merge the filters into one predicate
            final Predicate<DocRef> combinedPredicate = nameFilters.stream()
                    .map(nameFilter -> {
                        final Predicate<DocRef> predicate;
                        if (allowWildCards && PatternUtil.containsWildCards(nameFilter)) {
                            final Pattern pattern = PatternUtil.createPatternFromWildCardFilter(
                                    nameFilter, true);
                            predicate = docRef ->
                                    pattern.matcher(docRef.getName()).matches();
                        } else {
                            predicate = docRef ->
                                    nameFilter.equals(docRef.getName());
                        }
                        return predicate;
                    })
                    .reduce(PredicateUtil::orPredicates)
                    .orElse(val -> false); // no filters, no matches

            return list(types)
                    .stream()
                    .filter(combinedPredicate)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public Optional<String> getName(final DocRef d) {
        final Optional<DocRef> opt = map.keySet()
                .stream()
                .filter(docRef -> docRef.getUuid().equals(d.getUuid()))
                .findFirst();
        return opt.map(DocRef::getName);
    }

//    @Override
//    public List<DocRef> findByName(final String name) {
//        return map.keySet()
//                .stream()
//                .filter(docRef -> docRef.getName().equals(name))
//                .toList();
//    }

    @Override
    public ResultPage<DocAuditEntry> getAuditInfo(final DocRef docRef) {
        final List<DocAuditEntry> list = new ArrayList<>();
        try {
            final ImportExportDocument doc = read(docRef);
            if (doc != null) {
                final byte[] data = doc.toDataMap().get("meta");
                // Deserialise only the common AbstractDoc fields
                final GenericDoc document = JsonUtil.readValue(data, GenericDoc.class);
                list.add(new DocAuditEntry(document.getCreateTimeMs(),
                        new DocAuditUser(null, document.getCreateUser()), AuditAction.CREATE));
                list.add(new DocAuditEntry(document.getUpdateTimeMs(),
                        new DocAuditUser(null, document.getUpdateUser()), AuditAction.UPDATE));
                return ResultPage.createUnboundedList(list);
            }
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }

        return ResultPage.empty();
    }
}
