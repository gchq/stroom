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

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.node.api.NodeInfo;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.PathwayResultPage;
import stroom.pathways.shared.PathwaySummary;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.pathway.NamePathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.Pathway;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.dao.LmdbWriter;
import stroom.planb.impl.dao.ShardKeyRouter;
import stroom.planb.impl.dao.trace.NanoTimeUtil;
import stroom.planb.impl.dao.trace.PathwaysDb;
import stroom.planb.impl.fs.MergeCompletionStrategy;
import stroom.planb.impl.fs.SharedFileStorePublisher;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateType;
import stroom.util.io.PathCreator;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether a search sees the whole model when it is split across shards.
 *
 * <p>Pathways are written through the same shard store the consumer writes through, so what is read
 * back has really been pushed to the shared filesystem and copied down again — not handed over in
 * memory. The names are chosen so they do not all hash to one shard, which is the only way this says
 * anything about gathering.
 */
class TestShardedPathwayReader {

    private static final ByteBufferFactoryImpl BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final int SHARD_COUNT = 8;

    @TempDir
    Path tempDir;

    @Mock
    private PathCreator pathCreator;
    @Mock
    private NodeInfo nodeInfo;

    private PathwaysDoc doc;
    private PathwaysShardStore shardStore;
    private ShardedPathwayReader reader;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        final Path shared = Files.createDirectories(tempDir.resolve("pathways_shared"));
        doc = PathwaysDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Test Pathways")
                .sharedFileStore(new SharedFileStoreSettings(SHARD_COUNT, shared.toString()))
                .build();

        Mockito.when(pathCreator.toAppPath(Mockito.anyString())).thenReturn(tempDir.resolve("local"));
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("test-node");
        shardStore = new PathwaysShardStore(
                new SharedFileStorePublisher(
                        nodeInfo,
                        BYTE_BUFFERS,
                        BYTE_BUFFER_FACTORY,
                        new PlanBPaths(tempDir.resolve("local_state")),
                        Map.<StateType, MergeCompletionStrategy>of()),
                BYTE_BUFFERS,
                pathCreator);
        final PathwaySerde serde = new PathwaySerde(BYTE_BUFFER_FACTORY);
        reader = new ShardedPathwayReader(shardStore, serde, new MutationLog(BYTE_BUFFERS, serde));
    }

    @Test
    void aSearchGathersPathwaysFromEveryShard() throws IOException {
        final List<String> names = List.of("GET /orders", "FetchNewTasks.run", "POST /payments");
        assertThat(names.stream().map(this::shardOf).distinct().count())
                .as("the fixture only says something if the names hash apart")
                .isGreaterThan(1);
        for (final String name : names) {
            writePathway(name);
        }

        final PathwayResultPage page = reader.findPathways(doc, criteria(null, 0, 100));

        assertThat(page.getValues().stream().map(PathwaySummary::getName))
                .as("every shard contributed, in name order")
                .containsExactly("FetchNewTasks.run", "GET /orders", "POST /payments");
        assertThat(page.getPageResponse().getTotal()).isEqualTo(3L);
    }

    @Test
    void theGridsSortIsHonoured() throws IOException {
        writePathwayWithNodes("small", 1);
        writePathwayWithNodes("large", 60);
        writePathwayWithNodes("medium", 20);

        assertThat(names(reader.findPathways(doc, sortedBy(PathwaySummary.FIELD_SIZE, true, 0, 100))))
                .as("biggest first, which is not the order the names give")
                .containsExactly("large", "medium", "small");
        assertThat(names(reader.findPathways(doc, sortedBy(PathwaySummary.FIELD_SIZE, false, 0, 100))))
                .as("and the other way round")
                .containsExactly("small", "medium", "large");
    }

    @Test
    void theSortDecidesWhatGoesOnThePage() throws IOException {
        // Names ascending would put a-tiny and b-small on the first page. By size they belong on the
        // last one, so taking the page before sorting gives the wrong rows entirely rather than the
        // right rows in the wrong order.
        writePathwayWithNodes("a-tiny", 1);
        writePathwayWithNodes("b-small", 10);
        writePathwayWithNodes("c-big", 60);
        writePathwayWithNodes("d-large", 40);

        assertThat(names(reader.findPathways(doc, sortedBy(PathwaySummary.FIELD_SIZE, true, 0, 2))))
                .as("the first page holds the two biggest of all of them")
                .containsExactly("c-big", "d-large");
    }

    @Test
    void theFilterMatchesTheName() throws IOException {
        writePathway("GET /orders");
        writePathway("POST /payments");

        final PathwayResultPage page = reader.findPathways(doc, criteria("orders", 0, 100));

        assertThat(page.getValues().stream().map(PathwaySummary::getName)).containsExactly("GET /orders");
        assertThat(page.getPageResponse().getTotal()).isEqualTo(1L);
    }

    @Test
    void pagingRunsAcrossShardsInOneOrder() throws IOException {
        for (int i = 0; i < 6; i++) {
            writePathway("op-" + i);
        }

        final List<String> first = reader.findPathways(doc, criteria(null, 0, 2))
                .getValues().stream().map(PathwaySummary::getName).toList();
        final List<String> second = reader.findPathways(doc, criteria(null, 2, 2))
                .getValues().stream().map(PathwaySummary::getName).toList();

        assertThat(first).containsExactly("op-0", "op-1");
        assertThat(second).as("the second page continues where the first stopped")
                .containsExactly("op-2", "op-3");
        assertThat(reader.findPathways(doc, criteria(null, 0, 2)).getPageResponse().getTotal())
                .as("the total counts what matched, not what the page holds")
                .isEqualTo(6L);
    }

    @Test
    void aRowCostsNothingOfTheModelItStandsFor() throws IOException {
        // The grid shows four fields. A pathway holds every path it has seen, so reading one to render
        // a row is what made a page of them unopenable — the row must carry the size, not the model.
        writePathwayWithNodes("GET /orders", 500);

        final PathwaySummary summary = reader.findPathways(doc, criteria(null, 0, 100))
                .getValues().getFirst();

        assertThat(summary.getName()).isEqualTo("GET /orders");
        assertThat(summary.getCreateTime()).isNotNull();
        assertThat(summary.getUpdateTime()).isNotNull();
        assertThat(summary.getLastUsedTime()).isNotNull();
        assertThat(summary.getSizeBytes())
                .as("the row says how large the pathway it stands for is")
                .isGreaterThan(500L);
    }

    @Test
    void theWholePathwayComesBackOnlyWhenAskedForByName() throws IOException {
        writePathwayWithNodes("GET /orders", 20);
        writePathway("POST /payments");

        final Pathway fetched = reader.fetchPathway(doc, "GET /orders").orElseThrow();

        assertThat(fetched.getName()).isEqualTo("GET /orders");
        assertThat(fetched.getRoot()).as("this is the call that brings the model").isNotNull();
        assertThat(fetched.getRoot().getChildren()).isNotEmpty();
    }

    @Test
    void askingForAPathwayThatIsNotThereIsNotAnError() throws IOException {
        writePathway("GET /orders");

        assertThat(reader.fetchPathway(doc, "never heard of it")).isEmpty();
    }

    @Test
    void anEmptyModelIsNotAnError() {
        final PathwayResultPage page = reader.findPathways(doc, criteria(null, 0, 100));

        assertThat(page.getValues()).isEmpty();
        assertThat(page.getPageResponse().getTotal()).isZero();
    }

    @Test
    void aChangePushedSinceTheLastSearchIsSeen() throws IOException {
        writePathway("GET /orders");
        assertThat(reader.findPathways(doc, criteria(null, 0, 100)).getValues()).hasSize(1);

        // The cached local copy is only refreshed when the shard's version moves on, so a second
        // search has to notice that it did.
        writePathway("POST /payments");

        assertThat(reader.findPathways(doc, criteria(null, 0, 100)).getValues())
                .as("a search after a push sees what the push added")
                .hasSize(2);
    }

    /**
     * A publish writes the data file and then its version marker, so a crash between the two leaves a
     * shard holding a model no version describes. Reading it beats reporting the shard empty.
     */
    @Test
    void aShardWhoseDataHasNoVersionYetIsStillRead() throws IOException {
        writePathway("GET /orders");
        Files.delete(sharedShardDir("GET /orders").resolve(PlanBConstants.VERSION_FILE_NAME));

        assertThat(reader.findPathways(doc, criteria(null, 0, 100)).getValues())
                .as("data with no version marker is still the shard's model")
                .hasSize(1);
    }

    /**
     * And once a marker does appear it has to read as a change, or the copy taken while there was none
     * would be served for good. Both names go to one shard, so the second write is only visible if
     * that shard's copy is taken again.
     */
    @Test
    void theVersionAppearingAfterwardsRefreshesTheCopy() throws IOException {
        final String first = "GET /orders";
        final String second = sameShardAs(first);
        writePathway(first);
        Files.delete(sharedShardDir(first).resolve(PlanBConstants.VERSION_FILE_NAME));
        assertThat(reader.findPathways(doc, criteria(null, 0, 100)).getValues())
                .as("the copy is taken while the shard has no version marker")
                .hasSize(1);

        writePathway(second);

        assertThat(reader.findPathways(doc, criteria(null, 0, 100)).getValues())
                .as("the version the next publish writes reads as a change")
                .hasSize(2);
    }

    // -----------------------------------------------------------------------
    // Fixture
    // -----------------------------------------------------------------------

    // A different name that the router sends to the same shard, so a test can put two pathways in one
    // shard without depending on what the hash happens to do with any given pair.
    private String sameShardAs(final String name) {
        for (int i = 0; i < 10_000; i++) {
            final String candidate = "GET /other-" + i;
            if (shardOf(candidate) == shardOf(name)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No second name found for the shard holding " + name);
    }

    private Path sharedShardDir(final String name) {
        return Path.of(doc.getSharedFileStore().getSharedPath())
                .resolve(PlanBConstants.SHARDS_DIR_NAME)
                .resolve(doc.getUuid())
                .resolve(PlanBConstants.formatShardIndex(shardOf(name)));
    }

    private int shardOf(final String name) {
        return ShardKeyRouter.computeShardIndex(name, SHARD_COUNT);
    }

    // Writes one pathway through the shard store, so it goes to the shared filesystem the way the
    // consumer puts it there.
    private void writePathway(final String name) throws IOException {
        shardStore.withShard(doc, shardOf(name), localDir -> {
            try (final PathwaysDb db = PathwaysDb.create(localDir, BYTE_BUFFERS, false);
                    final LmdbWriter writer = db.createWriter()) {
                final Instant now = Instant.now();
                // Every field the serde writes has to be set; it reads them back positionally and
                // does not tolerate a gap. Mirrors what TraceProcessor builds for a new pathway.
                final Pathway pathway = Pathway.builder()
                        .name(name)
                        .createTime(NanoTimeUtil.fromInstant(now))
                        .updateTime(NanoTimeUtil.fromInstant(now))
                        .lastUsedTime(NanoTimeUtil.fromInstant(now))
                        .pathKey(new NamePathKey(name))
                        .root(new PathNode(name))
                        .build();
                final byte[] keyBytes = name.getBytes(StandardCharsets.UTF_8);
                final ByteBuffer key = ByteBuffer.allocateDirect(keyBytes.length);
                key.put(keyBytes).flip();
                // 0: nothing is stored under this name yet, so there is no size to go on.
                new PathwaySerde(BYTE_BUFFER_FACTORY).writePathway(pathway, 0, value ->
                        db.getPathways().insert(writer, key, value));
                writer.commit();
            }
            return true;
        });
    }

    // A root with `childCount` children, so the stored value is comfortably larger than its header.
    private void writePathwayWithNodes(final String name, final int childCount) throws IOException {
        shardStore.withShard(doc, shardOf(name), localDir -> {
            try (final PathwaysDb db = PathwaysDb.create(localDir, BYTE_BUFFERS, false);
                    final LmdbWriter writer = db.createWriter()) {
                final Instant now = Instant.now();
                final List<PathNode> children = new ArrayList<>();
                for (int i = 0; i < childCount; i++) {
                    children.add(new PathNode("child-" + i, List.of(name, "child-" + i)));
                }
                final PathNode root = PathNode.builder()
                        .uuid(UUID.randomUUID().toString())
                        .name(name)
                        .nodePath(List.of(name))
                        .children(children)
                        .build();
                final Pathway pathway = Pathway.builder()
                        .name(name)
                        .createTime(NanoTimeUtil.fromInstant(now))
                        .updateTime(NanoTimeUtil.fromInstant(now))
                        .lastUsedTime(NanoTimeUtil.fromInstant(now))
                        .pathKey(new NamePathKey(name))
                        .root(root)
                        .build();
                final byte[] keyBytes = name.getBytes(StandardCharsets.UTF_8);
                final ByteBuffer key = ByteBuffer.allocateDirect(keyBytes.length);
                key.put(keyBytes).flip();
                // 0: nothing is stored under this name yet, so there is no size to go on.
                new PathwaySerde(BYTE_BUFFER_FACTORY).writePathway(pathway, 0, value ->
                        db.getPathways().insert(writer, key, value));
                writer.commit();
            }
            return true;
        });
    }

    private static List<String> names(final PathwayResultPage page) {
        return page.getValues().stream().map(PathwaySummary::getName).toList();
    }

    private FindPathwayCriteria sortedBy(final String field,
                                         final boolean desc,
                                         final int offset,
                                         final int length) {
        return new FindPathwayCriteria(
                new PageRequest(offset, length),
                List.of(new CriteriaFieldSort(field, desc, true)),
                doc.asDocRef(),
                null,
                null);
    }

    private FindPathwayCriteria criteria(final String filter, final int offset, final int length) {
        return new FindPathwayCriteria(
                new PageRequest(offset, length),
                FindPathwayCriteria.DEFAULT_SORT_LIST,
                doc.asDocRef(),
                filter,
                null);
    }
}
