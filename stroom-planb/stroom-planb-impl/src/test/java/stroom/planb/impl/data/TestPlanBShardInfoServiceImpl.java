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

package stroom.planb.impl.data;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.node.api.NodeInfo;
import stroom.planb.impl.PlanBDocStore;
import stroom.planb.impl.dao.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.task.api.ExecutorProvider;
import stroom.util.io.FileUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This listing is an admin query over every shard on the node, so one unreadable shard must not
 * take the whole listing with it, and it must not open an env as a side effect of being read.
 */
class TestPlanBShardInfoServiceImpl {

    private static final String GOOD_UUID = "good-shard-uuid";
    private static final String BAD_UUID = "bad-shard-uuid";
    private static final String[] FIELDS = {
            PlanBShardInfoFields.NAME_FIELD.getFldName(),
            PlanBShardInfoFields.SHARD_TYPE_FIELD.getFldName(),
            PlanBShardInfoFields.SETTINGS_FIELD.getFldName()};

    /**
     * A shard whose doc cannot be read must not remove every other shard from the listing, which
     * would show an admin no shards at all and hide the very problem they are looking for.
     */
    @Test
    void oneUnreadableShardDoesNotLoseTheRest(@TempDir final Path tempDir) throws IOException {
        final StatePaths statePaths = createShardDirs(tempDir, GOOD_UUID, BAD_UUID);
        final PlanBDocStore docStore = Mockito.mock(PlanBDocStore.class);
        Mockito.when(docStore.readDocument(docRef(GOOD_UUID))).thenReturn(doc(GOOD_UUID, "Good"));
        Mockito.when(docStore.readDocument(docRef(BAD_UUID)))
                .thenThrow(new RuntimeException("Cannot read this doc"));

        final List<String[]> results = createService(statePaths, docStore, adminSecurityContext(),
                Mockito.mock(ShardManager.class)).getStoreInfo(FIELDS);

        assertThat(names(results)).containsExactly("Good");
    }

    /**
     * A shard whose doc has been deleted is expected rather than exceptional, and is simply
     * omitted.
     */
    @Test
    void deletedDocsShardIsOmitted(@TempDir final Path tempDir) throws IOException {
        final StatePaths statePaths = createShardDirs(tempDir, GOOD_UUID, BAD_UUID);
        final PlanBDocStore docStore = Mockito.mock(PlanBDocStore.class);
        Mockito.when(docStore.readDocument(docRef(GOOD_UUID))).thenReturn(doc(GOOD_UUID, "Good"));
        Mockito.when(docStore.readDocument(docRef(BAD_UUID)))
                .thenThrow(new DocumentNotFoundException(docRef(BAD_UUID)));

        final List<String[]> results = createService(statePaths, docStore, adminSecurityContext(),
                Mockito.mock(ShardManager.class)).getStoreInfo(FIELDS);

        assertThat(names(results)).containsExactly("Good");
    }

    /**
     * Without the VIEW check any user able to query this datasource saw every shard's name, size
     * and settings.
     */
    @Test
    void shardsTheUserCannotViewAreOmitted(@TempDir final Path tempDir) throws IOException {
        final StatePaths statePaths = createShardDirs(tempDir, GOOD_UUID, BAD_UUID);
        final PlanBDocStore docStore = Mockito.mock(PlanBDocStore.class);
        Mockito.when(docStore.readDocument(docRef(GOOD_UUID))).thenReturn(doc(GOOD_UUID, "Visible"));
        Mockito.when(docStore.readDocument(docRef(BAD_UUID))).thenReturn(doc(BAD_UUID, "Hidden"));

        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.isAdmin()).thenReturn(false);
        Mockito.when(securityContext.hasDocumentPermission(docRef(GOOD_UUID), DocumentPermission.VIEW))
                .thenReturn(true);
        Mockito.when(securityContext.hasDocumentPermission(docRef(BAD_UUID), DocumentPermission.VIEW))
                .thenReturn(false);

        final List<String[]> results = createService(statePaths, docStore, securityContext,
                Mockito.mock(ShardManager.class)).getStoreInfo(FIELDS);

        assertThat(names(results)).containsExactly("Visible");
        // Not even asked about, so a hidden shard's doc is never read.
        Mockito.verify(docStore, Mockito.never()).readDocument(docRef(BAD_UUID));
    }

    /**
     * Reading this listing must not open an env. getShardForMapName() creates a shard if there is
     * not one, so after a restart an admin query would open every store on the node, one per row.
     */
    @Test
    void doesNotCreateShardsToReportOnThem(@TempDir final Path tempDir) throws IOException {
        final StatePaths statePaths = createShardDirs(tempDir, GOOD_UUID);
        final PlanBDocStore docStore = Mockito.mock(PlanBDocStore.class);
        Mockito.when(docStore.readDocument(docRef(GOOD_UUID))).thenReturn(doc(GOOD_UUID, "Good"));
        final ShardManager shardManager = Mockito.mock(ShardManager.class);
        Mockito.when(shardManager.getExistingShard(GOOD_UUID)).thenReturn(Optional.empty());

        final List<String[]> results = createService(statePaths, docStore, adminSecurityContext(), shardManager)
                .getStoreInfo(FIELDS);

        // Still listed, just with no settings to report.
        assertThat(names(results)).containsExactly("Good");
        assertThat(settings(results)).containsExactly((String) null);
        Mockito.verify(shardManager, Mockito.never()).getShardForMapName(Mockito.any());
        Mockito.verify(shardManager, Mockito.never()).getShardForDocUuid(Mockito.any());
    }

    /**
     * A shard closed between the lookup and the call must still be listed. The row is only added
     * once every field is resolved, so letting this escape makes a failing shard invisible.
     */
    @Test
    void shardClosedWhileBeingReadIsStillListed(@TempDir final Path tempDir) throws IOException {
        final StatePaths statePaths = createShardDirs(tempDir, GOOD_UUID);
        final PlanBDocStore docStore = Mockito.mock(PlanBDocStore.class);
        Mockito.when(docStore.readDocument(docRef(GOOD_UUID))).thenReturn(doc(GOOD_UUID, "Good"));

        final Shard shard = Mockito.mock(Shard.class);
        Mockito.when(shard.getInfo()).thenThrow(new SnapshotShard.ShardClosedException());
        final ShardManager shardManager = Mockito.mock(ShardManager.class);
        Mockito.when(shardManager.getExistingShard(GOOD_UUID)).thenReturn(Optional.of(shard));

        final List<String[]> results = createService(statePaths, docStore, adminSecurityContext(), shardManager)
                .getStoreInfo(FIELDS);

        assertThat(names(results)).containsExactly("Good");
        assertThat(settings(results)).containsExactly((String) null);
    }

    @Test
    void reportsAnOpenShardsSettings(@TempDir final Path tempDir) throws IOException {
        final StatePaths statePaths = createShardDirs(tempDir, GOOD_UUID);
        final PlanBDocStore docStore = Mockito.mock(PlanBDocStore.class);
        Mockito.when(docStore.readDocument(docRef(GOOD_UUID))).thenReturn(doc(GOOD_UUID, "Good"));

        final Shard shard = Mockito.mock(Shard.class);
        Mockito.when(shard.getInfo()).thenReturn("the shard info");
        final ShardManager shardManager = Mockito.mock(ShardManager.class);
        Mockito.when(shardManager.getExistingShard(GOOD_UUID)).thenReturn(Optional.of(shard));

        final List<String[]> results = createService(statePaths, docStore, adminSecurityContext(), shardManager)
                .getStoreInfo(FIELDS);

        assertThat(settings(results)).containsExactly("the shard info");
        assertThat(column(results, PlanBShardInfoFields.SHARD_TYPE_FIELD.getFldName())).containsExactly("Shard");
    }

    @Test
    void toleratesNoShardDirAtAll(@TempDir final Path tempDir) {
        final PlanBDocStore docStore = Mockito.mock(PlanBDocStore.class);

        final List<String[]> results = createService(new StatePaths(tempDir), docStore, adminSecurityContext(),
                Mockito.mock(ShardManager.class)).getStoreInfo(FIELDS);

        assertThat(results).isEmpty();
        Mockito.verifyNoInteractions(docStore);
    }

    private SecurityContext adminSecurityContext() {
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.isAdmin()).thenReturn(true);
        return securityContext;
    }

    private DocRef docRef(final String uuid) {
        return DocRef.builder().type(PlanBDoc.TYPE).uuid(uuid).build();
    }

    private PlanBDoc doc(final String uuid, final String name) {
        // A real doc rather than a mock, as AbstractDoc.getName() is final.
        return PlanBDoc.builder().uuid(uuid).name(name).build();
    }

    private StatePaths createShardDirs(final Path tempDir, final String... uuids) throws IOException {
        final StatePaths statePaths = new StatePaths(tempDir);
        FileUtil.ensureDirExists(statePaths.getShardDir());
        for (final String uuid : uuids) {
            Files.createDirectories(statePaths.getShardDir().resolve(uuid));
        }
        return statePaths;
    }

    private List<String> names(final List<String[]> results) {
        return column(results, PlanBShardInfoFields.NAME_FIELD.getFldName());
    }

    private List<String> settings(final List<String[]> results) {
        return column(results, PlanBShardInfoFields.SETTINGS_FIELD.getFldName());
    }

    private List<String> column(final List<String[]> results, final String field) {
        final int index = Arrays.asList(FIELDS).indexOf(field);
        return results.stream().map(values -> values[index]).toList();
    }

    private PlanBShardInfoServiceImpl createService(final StatePaths statePaths,
                                                    final PlanBDocStore docStore,
                                                    final SecurityContext securityContext,
                                                    final ShardManager shardManager) {
        final ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
        Mockito.when(executorProvider.get()).thenReturn(Runnable::run);
        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("node1");
        return new PlanBShardInfoServiceImpl(
                securityContext,
                null,
                null,
                null,
                null,
                () -> nodeInfo,
                null,
                null,
                statePaths,
                docStore,
                shardManager,
                executorProvider);
    }
}
