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
import stroom.docstore.api.DocumentResourceHelper;
import stroom.util.shared.EntityServiceException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The resource is mostly delegation to {@link DocumentResourceHelper}; the only logic of its
 * own is the guard that stops a doc being written to a different UUID than the path names.
 * <p>
 * Note that the {@code doc.getUuid() == null} half of that guard is not covered here because it
 * is unreachable - {@link stroom.docstore.shared.AbstractDoc} rejects a null uuid in its
 * constructor, so no {@link DataGenDoc} can exist with one, deserialised or otherwise.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TestDataGenResourceImpl {

    private static final String UUID = "doc-uuid-1";

    @Mock
    private DataGenStore dataGenStore;
    @Mock
    private DocumentResourceHelper documentResourceHelper;

    private DataGenResourceImpl resource;

    @BeforeEach
    void setUp() {
        resource = new DataGenResourceImpl(() -> dataGenStore, () -> documentResourceHelper);
    }

    @Test
    void fetch_readsFromTheStoreUsingADataGenDocRef() {
        final DataGenDoc doc = doc(UUID);
        when(documentResourceHelper.read(eq(dataGenStore), any(DocRef.class))).thenReturn(doc);

        assertThat(resource.fetch(UUID))
                .isSameAs(doc);

        final ArgumentCaptor<DocRef> captor = ArgumentCaptor.forClass(DocRef.class);
        verify(documentResourceHelper).read(eq(dataGenStore), captor.capture());
        assertThat(captor.getValue().getType())
                .describedAs("Must look the doc up as a DataGen, not some other type")
                .isEqualTo(DataGenDoc.TYPE);
        assertThat(captor.getValue().getUuid())
                .isEqualTo(UUID);
    }

    @Test
    void update_uuidsMatch_updatesTheDoc() {
        final DataGenDoc doc = doc(UUID);
        when(documentResourceHelper.update(dataGenStore, doc)).thenReturn(doc);

        assertThat(resource.update(UUID, doc))
                .isSameAs(doc);

        verify(documentResourceHelper).update(dataGenStore, doc);
    }

    @Test
    void update_uuidsDoNotMatch_throwsAndDoesNotWrite() {
        final DataGenDoc doc = doc("a-different-uuid");

        assertThatThrownBy(() -> resource.update(UUID, doc))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("UUID");

        verifyNoInteractions(documentResourceHelper);
    }

    private static DataGenDoc doc(final String uuid) {
        return DataGenDoc.builder()
                .uuid(uuid)
                .name("My Generator")
                .build();
    }
}
