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

package stroom.pathways.impl;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.node.api.NodeInfo;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.PathwaysDoc;
import stroom.planb.impl.data.ShardManager;
import stroom.util.io.PathCreator;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class TestPathwaysProcessor {

    private static final String LIVE_UUID = "live-doc-uuid";
    private static final String DELETED_UUID = "deleted-doc-uuid";
    private static final String NOT_FOUND_UUID = "not-found-doc-uuid";
    private static final String UNKNOWN_UUID = "unknown-doc-uuid";

    /**
     * A deleted doc's pathway data is not reclaimed by anything else, so it would otherwise sit
     * on disk, with its env open, for the life of the process.
     */
    @Test
    void deletesStoresOfDeletedDocsOnly(@TempDir final Path tempDir) throws IOException {
        final Path live = createStoreDir(tempDir, LIVE_UUID);
        final Path deleted = createStoreDir(tempDir, DELETED_UUID);
        final Path notFound = createStoreDir(tempDir, NOT_FOUND_UUID);
        final Path unknown = createStoreDir(tempDir, UNKNOWN_UUID);

        final PathwaysStore pathwaysStore = Mockito.mock(PathwaysStore.class);
        // An empty list must NOT be taken to mean every doc has been deleted, so the store is
        // asked about each dir individually.
        Mockito.when(pathwaysStore.list()).thenReturn(Collections.emptyList());
        Mockito.when(pathwaysStore.readDocument(docRef(LIVE_UUID)))
                .thenReturn(Mockito.mock(PathwaysDoc.class));
        Mockito.when(pathwaysStore.readDocument(docRef(DELETED_UUID)))
                .thenReturn(null);
        Mockito.when(pathwaysStore.readDocument(docRef(NOT_FOUND_UUID)))
                .thenThrow(new DocumentNotFoundException(docRef(NOT_FOUND_UUID)));
        // Anything other than a definite "not found" must leave the store alone. A permission
        // failure is the realistic case: the docstore throws rather than returning null.
        Mockito.when(pathwaysStore.readDocument(docRef(UNKNOWN_UUID)))
                .thenThrow(new PermissionException(null, "Not authorised"));

        createProcessor(tempDir, pathwaysStore).exec();

        assertThat(deleted).doesNotExist();
        assertThat(notFound).doesNotExist();
        assertThat(live).exists();
        assertThat(unknown).exists();
    }

    /**
     * The store being deleted is normally open, so this covers the close() that
     * PathwaysDb gained for exactly this purpose, and the deletion of a real env's files.
     */
    @Test
    void closesAnOpenStoreBeforeDeletingIt(@TempDir final Path tempDir) {
        final PathwaysStore pathwaysStore = Mockito.mock(PathwaysStore.class);
        Mockito.when(pathwaysStore.list()).thenReturn(Collections.emptyList());
        Mockito.when(pathwaysStore.readDocument(docRef(DELETED_UUID)))
                .thenThrow(new DocumentNotFoundException(docRef(DELETED_UUID)));

        final Path storeDir = tempDir.resolve("pathways").resolve(DELETED_UUID);
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        final PathwaysProcessor processor = createProcessor(
                tempDir, pathwaysStore, new ByteBuffers(byteBufferFactory));

        // Opens a real env on disk, as processing would.
        processor.findPathways(criteria(DELETED_UUID));
        assertThat(storeDir).doesNotExist();

        // Now make one exist and be open, then let the sweep close and delete it.
        processor.openForTesting(docRef(DELETED_UUID));
        assertThat(storeDir.resolve("data.mdb")).exists();

        processor.exec();

        assertThat(storeDir).doesNotExist();
    }

    @Test
    void toleratesNoPathwaysDirAtAll(@TempDir final Path tempDir) {
        final PathwaysStore pathwaysStore = Mockito.mock(PathwaysStore.class);
        Mockito.when(pathwaysStore.list()).thenReturn(Collections.emptyList());

        createProcessor(tempDir, pathwaysStore).exec();

        Mockito.verify(pathwaysStore, Mockito.never()).readDocument(Mockito.any());
    }

    private DocRef docRef(final String uuid) {
        return DocRef.builder().type(PathwaysDoc.TYPE).uuid(uuid).build();
    }

    private Path createStoreDir(final Path tempDir, final String uuid) throws IOException {
        final Path dir = tempDir.resolve("pathways").resolve(uuid);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("data.mdb"), "pathway data for " + uuid);
        return dir;
    }

    private FindPathwayCriteria criteria(final String uuid) {
        return new FindPathwayCriteria(PageRequest.createDefault(), null, docRef(uuid));
    }

    private PathwaysProcessor createProcessor(final Path tempDir, final PathwaysStore pathwaysStore) {
        return createProcessor(tempDir, pathwaysStore, null);
    }

    private PathwaysProcessor createProcessor(final Path tempDir,
                                              final PathwaysStore pathwaysStore,
                                              final ByteBuffers byteBuffers) {
        final PathCreator pathCreator = Mockito.mock(PathCreator.class);
        Mockito.when(pathCreator.toAppPath("${stroom.home}/pathways")).thenReturn(tempDir);
        return new PathwaysProcessor(
                pathwaysStore,
                Mockito.mock(MessageReceiverFactory.class),
                pathCreator,
                byteBuffers,
                null,
                Mockito.mock(ShardManager.class),
                Mockito.mock(NodeInfo.class));
    }
}
