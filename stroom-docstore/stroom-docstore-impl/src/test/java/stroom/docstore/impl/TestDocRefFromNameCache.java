/*
 * Copyright 2026 Crown Copyright
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

package stroom.docstore.impl;

import stroom.cache.impl.CacheManagerImpl;
import stroom.docref.DocRef;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/// Reproductions for stale XSLT name lookups (used to resolve `xsl:import`/`xsl:include` by name).
///
/// The persistence is faked with a list of documents whose names change on rename, as the DB persistence's do.
/// Each test fires the entity events the store fires for the change (see `StoreImpl`).
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestDocRefFromNameCache {

    private static final String TYPE = "XSLT";

    private final CacheManagerImpl cacheManager = new CacheManagerImpl();
    private final List<DocRef> docs = new ArrayList<>();
    private final AtomicBoolean persistenceFails = new AtomicBoolean();

    @Mock
    private Persistence persistence;

    private DocRefFromNameCache cache;

    @BeforeEach
    void setUp() {
        Mockito.when(persistence.find(Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean()))
                .thenAnswer(invocation -> {
                    if (persistenceFails.get()) {
                        throw new RuntimeException("Persistence unavailable");
                    }
                    final String type = invocation.getArgument(0);
                    final String name = invocation.getArgument(1);
                    return docs.stream()
                            .filter(docRef -> type.equals(docRef.getType()) && name.equals(docRef.getName()))
                            .toList();
                });
        cache = new DocRefFromNameCache(cacheManager, DocStoreConfig::new, persistence);
    }

    /// After a rename, a lookup of the old name must not find the renamed document.
    @Test
    void oldNameIsNotFoundAfterRename() {
        final DocRef original = create("old");
        assertThat(cache.get(TYPE, "old")).as("before the rename").containsExactly(original);

        final DocRef renamed = rename(original, "new");

        assertThat(cache.get(TYPE, "new")).as("new name after the rename").containsExactly(renamed);
        assertThat(cache.get(TYPE, "old")).as("old name after the rename").isEmpty();
    }

    /// A name looked up before any document had it (e.g. an import of an XSLT not yet made) must be found
    /// once a document is renamed to it.
    @Test
    void previouslyMissingNameIsFoundAfterRenameToIt() {
        final DocRef existing = create("existing");
        assertThat(cache.get(TYPE, "wanted")).as("before the rename").isEmpty();

        final DocRef renamed = rename(existing, "wanted");

        assertThat(cache.get(TYPE, "wanted")).as("after the rename").containsExactly(renamed);
    }

    /// As [#previouslyMissingNameIsFoundAfterRenameToIt()], for a newly created document.
    @Test
    void previouslyMissingNameIsFoundAfterCreate() {
        assertThat(cache.get(TYPE, "wanted")).as("before the create").isEmpty();

        final DocRef created = create("wanted");

        assertThat(cache.get(TYPE, "wanted")).as("after the create").containsExactly(created);
    }

    /// An XSLT is copied (the copy keeps the name) and the copy then renamed. A lookup of the original name
    /// must find only the original, not report two matches (`Found 2 resources for: ...`).
    @Test
    void copyThenRenameLeavesOneMatchForTheOriginalName() {
        final DocRef original = create("shared");
        final DocRef copy = create("shared");
        assertThat(cache.get(TYPE, "shared")).as("after the copy").containsExactlyInAnyOrder(original, copy);

        rename(copy, "shared-copy");

        assertThat(cache.get(TYPE, "shared")).as("original name after renaming the copy").containsExactly(original);
    }

    /// A lookup that fails (e.g. a transient DB error) must not be remembered as "no such document".
    @Test
    void failedLookupIsNotRemembered() {
        final DocRef doc = create("doc");
        persistenceFails.set(true);
        assertThat(cache.get(TYPE, "doc")).as("while persistence fails").isEmpty();

        persistenceFails.set(false);

        assertThat(cache.get(TYPE, "doc")).as("once persistence recovers").containsExactly(doc);
    }

    private DocRef create(final String name) {
        final DocRef docRef = DocRef.builder().type(TYPE).randomUuid().name(name).build();
        docs.add(docRef);
        cache.onChange(new EntityEvent(docRef, EntityAction.CREATE));
        return docRef;
    }

    // Fires what a rename from the explorer fires: StoreImpl.update fires UPDATE with the old doc ref, then
    // ExplorerServiceImpl.rename fires UPDATE_EXPLORER_NODE and UPDATE with the new doc ref only.
    private DocRef rename(final DocRef docRef, final String newName) {
        final DocRef renamed = docRef.copy().name(newName).build();
        docs.remove(docRef);
        docs.add(renamed);
        cache.onChange(new EntityEvent(renamed, docRef, EntityAction.UPDATE));
        cache.onChange(new EntityEvent(renamed, EntityAction.UPDATE_EXPLORER_NODE));
        cache.onChange(new EntityEvent(renamed, EntityAction.UPDATE));
        return renamed;
    }
}
