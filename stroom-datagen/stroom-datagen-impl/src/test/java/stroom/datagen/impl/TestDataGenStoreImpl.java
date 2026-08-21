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

package stroom.datagen.impl;

import stroom.datagen.shared.DataGenDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.api.SecurityContext;
import stroom.util.shared.EntityServiceException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestDataGenStoreImpl {

    private static final DocRef FEED_DOC_REF = DocRef.builder()
            .type(FeedDoc.TYPE)
            .uuid("feed-uuid-1")
            .name("TEST-FEED")
            .build();

    @Mock
    private StoreFactory storeFactory;
    @Mock
    private DataGenSerialiser serialiser;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Store<DataGenDoc> store;

    private DataGenStoreImpl dataGenStore;

    @BeforeEach
    void setUp() {
        // createStore is generic in D and infers AbstractDoc from the any() matchers, so the
        // untyped doReturn form is needed here.
        doReturn(store).when(storeFactory).createStore(any(), eq(DataGenDoc.TYPE), any(), any(), any());
        // writeDocument() is authorised by AbstractDocumentStore with EDIT, against a DocRef it builds
        // from the store's type. These tests are about the feed validation that happens on top of that,
        // not about the check itself, so give it a type and grant the permission - lenient because the
        // bad-feed test throws before ever reaching either.
        lenient().when(store.getType()).thenReturn(DataGenDoc.TYPE);
        lenient().when(securityContext.hasDocumentPermission(any(), any())).thenReturn(true);
        dataGenStore = new DataGenStoreImpl(storeFactory, securityContext, serialiser);
    }


    // --------------------------------------------------------------------------------
    // writeDocument() - the destination feed must actually be a feed.
    //
    // The UI restricts the picker to FeedDoc.TYPE, so a wrong type can only arrive over the
    // REST API. Left unvalidated it persists happily and only surfaces when the generator
    // next runs.
    // --------------------------------------------------------------------------------

    @Test
    void writeDocument_feedIsAFeed_writes() {
        final DataGenDoc doc = doc(FEED_DOC_REF);
        when(store.writeDocument(doc)).thenReturn(doc);

        assertThat(dataGenStore.writeDocument(doc))
                .isSameAs(doc);

        verify(store).writeDocument(doc);
    }

    @Test
    void writeDocument_feedNotSet_writes() {
        // A partially configured doc must still be saveable.
        final DataGenDoc doc = doc(null);
        when(store.writeDocument(doc)).thenReturn(doc);

        assertThat(dataGenStore.writeDocument(doc))
                .isSameAs(doc);

        verify(store).writeDocument(doc);
    }

    @Test
    void writeDocument_feedIsNotAFeed_throwsAndDoesNotWrite() {
        final DataGenDoc doc = doc(DocRef.builder()
                .type(PipelineDoc.TYPE)
                .uuid("pipeline-uuid-1")
                .name("Not A Feed")
                .build());

        assertThatThrownBy(() -> dataGenStore.writeDocument(doc))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining(FeedDoc.TYPE)
                .hasMessageContaining(PipelineDoc.TYPE);

        verifyNoInteractions(store);
    }


    // --------------------------------------------------------------------------------
    // getDependencyRemapFunction()
    //
    // Drives two things in StoreImpl: remapping the feed reference when a doc is imported or
    // copied with new UUIDs, and registering the DataGen -> Feed edge in the dependency graph.
    // Returning null (the default) silently disables both.
    // --------------------------------------------------------------------------------

    @Test
    void dependencyRemap_declaresTheFeedAsADependency() {
        final DependencyRemapper remapper = new DependencyRemapper();

        remapFunction().remap(doc(FEED_DOC_REF), remapper);

        assertThat(remapper.getDependencies())
                .describedAs("Without this the explorer records no dependency on the feed")
                .containsExactly(FEED_DOC_REF);
    }

    @Test
    void dependencyRemap_rewritesTheFeedToItsNewDocRef() {
        final DocRef importedFeed = DocRef.builder()
                .type(FeedDoc.TYPE)
                .uuid("feed-uuid-2")
                .name("TEST-FEED")
                .build();
        final DependencyRemapper remapper = new DependencyRemapper(Map.of(FEED_DOC_REF, importedFeed));

        final DataGenDoc remapped = remapFunction().remap(doc(FEED_DOC_REF), remapper);

        assertThat(remapped.getFeed())
                .describedAs("An imported doc must point at the imported feed, not the source system's")
                .isEqualTo(importedFeed);
        assertThat(remapper.isChanged())
                .isTrue();
    }

    @Test
    void dependencyRemap_feedNotSet_isANoOp() {
        final DependencyRemapper remapper = new DependencyRemapper();

        final DataGenDoc remapped = remapFunction().remap(doc(null), remapper);

        assertThat(remapped.getFeed())
                .isNull();
        assertThat(remapper.getDependencies())
                .isEmpty();
    }

    @Test
    void dependencyRemap_preservesTheRestOfTheDoc() {
        final DocRef importedFeed = FEED_DOC_REF.copy().uuid("feed-uuid-2").build();
        final DependencyRemapper remapper = new DependencyRemapper(Map.of(FEED_DOC_REF, importedFeed));

        final DataGenDoc original = doc(FEED_DOC_REF);
        final DataGenDoc remapped = remapFunction().remap(original, remapper);

        assertThat(remapped.getTemplate())
                .isEqualTo(original.getTemplate());
        assertThat(remapped.getUuid())
                .isEqualTo(original.getUuid());
        assertThat(remapped.getName())
                .isEqualTo(original.getName());
    }

    private DependencyRemapFunction<DataGenDoc> remapFunction() {
        final DependencyRemapFunction<DataGenDoc> function = dataGenStore.getDependencyRemapFunction();
        assertThat(function)
                .describedAs("A null function disables remapping and dependency tracking entirely")
                .isNotNull();
        return function;
    }

    private static DataGenDoc doc(final DocRef feed) {
        return DataGenDoc.builder()
                .uuid("doc-uuid-1")
                .name("My Generator")
                .template("some generated data")
                .feed(feed)
                .build();
    }
}
