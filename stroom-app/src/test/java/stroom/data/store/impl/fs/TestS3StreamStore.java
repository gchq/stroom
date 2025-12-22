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

package stroom.data.store.impl.fs;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.api.TargetUtil;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.feed.api.FeedStore;
import stroom.meta.api.EffectiveMeta;
import stroom.meta.api.EffectiveMetaDataCriteria;
import stroom.meta.api.EffectiveMetaSet;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.impl.MetaValueConfig;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.Period;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

class TestS3StreamStore extends AbstractCoreIntegrationTest {

    private static final int N1 = 1;
    private static final int N13 = 13;

    private static final String FEED1 = "FEED1";
    private static final String FEED2 = "FEED2";
    private static final String FEED3 = "FEED3";

    @Inject
    private Store streamStore;
    @Inject
    private MetaService metaService;
    @Inject
    private DataVolumeService dataVolumeService;
    @Inject
    private FsPathHelper pathHelper;
    @Inject
    private FeedStore feedService;
    @Inject
    private ExplorerNodeService explorerNodeService;

    private DocRef feed1;
    private DocRef feed2;
    private DocRef feed3;
    private DocRef folder2;

    @BeforeEach
    void setProperties() {
        feed1 = setupFeed("FEED1");
        feed2 = setupFeed("FEED2");
        feed3 = setupFeed("FEED3");

        // Make sure stream attributes get flushed straight away.
        setConfigValueMapper(MetaValueConfig.class, metaValueConfig -> metaValueConfig.withAddAsync(false));

        final ExplorerNode system = explorerNodeService.getRoot();
        final DocRef root = system.getDocRef();
        final DocRef folder1 = new DocRef("Folder", UUID.randomUUID().toString(), "Folder 1");
        folder2 = new DocRef("Folder", UUID.randomUUID().toString(), "Folder 2");
        final DocRef folder3 = new DocRef("Folder", UUID.randomUUID().toString(), "Folder 3");

        explorerNodeService.createNode(folder1, root, PermissionInheritance.NONE);
        explorerNodeService.createNode(folder2, folder1, PermissionInheritance.NONE);
        explorerNodeService.createNode(folder3, root, PermissionInheritance.NONE);
        explorerNodeService.createNode(feed1, folder2, PermissionInheritance.NONE);
        explorerNodeService.createNode(feed2, folder2, PermissionInheritance.NONE);
    }

    @AfterEach
    void unsetProperties() {
        clearConfigValueMapper();
    }

    /**
     * Setup some test data.
     */
    private DocRef setupFeed(final String feedName) {
        final List<DocRef> docRefs = feedService.findByName(feedName);
        if (docRefs != null && docRefs.size() > 0) {
            return docRefs.get(0);
        }
        return feedService.createDocument(feedName);
    }

    @Test
    void testBasic() throws IOException {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addDateTerm(MetaFields.CREATE_TIME, Condition.BETWEEN, createYearPeriod(2014))
                .addDateTerm(MetaFields.EFFECTIVE_TIME, Condition.BETWEEN, createYearPeriod(2014))
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED1)
                .addIdTerm(MetaFields.PARENT_ID, Condition.EQUALS, 1)
                .addIdTerm(MetaFields.ID, Condition.EQUALS, 1)
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
        testCriteria(new FindMetaCriteria(expression), 0);
    }

    private String createYearPeriod(final int year) {
        return year + "-01-01T00:00:00.000Z," + (year + 1) + "-01-01T00:00:00.000Z";
    }

    private String createToDateWithOffset(final long time, final int offset) {
        final long from = time;
        final long to = time + (offset * 1000 * 60 * 60 * 24);
        return DateUtil.createNormalDateTimeString(from) + "," + DateUtil.createNormalDateTimeString(to);
    }

    @Test
    void testFeedFindAll() throws IOException {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED1)
                        .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED2)
                        .build())
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
        testCriteria(new FindMetaCriteria(expression), 2);
    }

    @Test
    void testFeedFindSome() throws IOException {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED1)
                        .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED2)
                        .build())
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(expression);
        findMetaCriteria.setPageRequest(PageRequest.oneRow());
        testCriteria(findMetaCriteria, 1);
    }

    @Test
    void testFeedFindNone() throws IOException {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED1)
                .addOperator(ExpressionOperator.builder().op(Op.NOT)
                        .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED1)
                        .build())
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
        testCriteria(new FindMetaCriteria(expression), 0);
    }

    @Test
    void testFeedFindOne() throws IOException {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED2)
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
        testCriteria(new FindMetaCriteria(expression), 1);
    }

    @Test
    void testFolder() throws Exception {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addDocRefTerm(MetaFields.FEED, Condition.IN_FOLDER, folder2)
                .build();
        testCriteria(new FindMetaCriteria(expression), 2);
    }

    private void testCriteria(final FindMetaCriteria criteria, final int expectedStreams) throws IOException {
        metaService.updateStatus(new FindMetaCriteria(), null, Status.DELETED);

        createMeta(FEED1, null);
        createMeta(FEED2, null);
        createMeta(FEED3, null);

//        criteria.obtainStatusSet().add(StreamStatus.UNLOCKED);
        final ResultPage<Meta> streams = metaService.find(criteria);
        assertThat(streams.size()).isEqualTo(expectedStreams);

        metaService.updateStatus(new FindMetaCriteria(), null, Status.DELETED);
    }

    @Test
    void testParentChild() throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();
        final Meta rootMeta;
        try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
            rootMeta = streamTarget.getMeta();
            TargetUtil.write(streamTarget, testString);
        }

        final MetaProperties childProperties = MetaProperties.builder()
                .feedName(rootMeta.getFeedName())
                .typeName(StreamTypeNames.RAW_EVENTS)
                .parent(rootMeta)
                .build();
        final Meta childMeta;
        try (final Target childTarget = streamStore.openTarget(childProperties)) {
            childMeta = childTarget.getMeta();
            TargetUtil.write(childTarget, testString);
        }

        final MetaProperties grandChildProperties = MetaProperties.builder()
                .feedName(childMeta.getFeedName())
                .typeName(StreamTypeNames.RAW_EVENTS)
                .parent(childMeta)
                .build();
        final Meta grandChildMeta;
        try (final Target grandChildTarget = streamStore.openTarget(grandChildProperties)) {
            grandChildMeta = grandChildTarget.getMeta();
            TargetUtil.write(grandChildTarget, testString);
        }

        List<MetaRow> relationList = metaService.findRelatedData(childMeta.getId(), true);

        assertThat(relationList.size()).isEqualTo(3);
        assertThat(relationList.get(0).getMeta()).isEqualTo(rootMeta);
        assertThat(relationList.get(1).getMeta()).isEqualTo(childMeta);
        assertThat(relationList.get(2).getMeta()).isEqualTo(grandChildMeta);

        relationList = metaService.findRelatedData(grandChildMeta.getId(), true);

        assertThat(relationList.size()).isEqualTo(3);
        assertThat(relationList.get(0).getMeta()).isEqualTo(rootMeta);
        assertThat(relationList.get(1).getMeta()).isEqualTo(childMeta);
        assertThat(relationList.get(2).getMeta()).isEqualTo(grandChildMeta);
    }

    @Test
    void testFindDeleteAndUndelete() throws IOException {
        final Meta meta = createMeta(FEED1, null);

        FindMetaCriteria findMetaCriteria = FindMetaCriteria.createFromMeta(meta);
        final long deleted = metaService.updateStatus(findMetaCriteria, Status.UNLOCKED, Status.DELETED);

        assertThat(deleted).isEqualTo(1L);

        findMetaCriteria = new FindMetaCriteria(MetaExpressionUtil.createDataIdExpression(meta.getId()));
        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(1L);

        findMetaCriteria.setExpression(MetaExpressionUtil.createStatusExpression(Status.UNLOCKED));
        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(0L);

        findMetaCriteria.setExpression(MetaExpressionUtil.createStatusExpression(Status.DELETED));
        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(1L);

        findMetaCriteria.setExpression(MetaExpressionUtil.createStatusExpression(Status.UNLOCKED));
        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(0L);

        // This will undelete
        findMetaCriteria.setExpression(MetaExpressionUtil.createStatusExpression(Status.DELETED));
        assertThat(metaService.updateStatus(findMetaCriteria, Status.DELETED, Status.UNLOCKED)).isEqualTo(1L);

        findMetaCriteria.setExpression(MetaExpressionUtil.createStatusExpression(Status.UNLOCKED));
        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(1L);
    }

    private Meta createMeta(final String feedName, final Long parentStreamId) throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .parentId(parentStreamId)
                .build();

        try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
            TargetUtil.write(streamTarget, testString);
            return streamTarget.getMeta();
        }
    }

    @Test
    void testFindWithAllCriteria() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addDateTerm(MetaFields.CREATE_TIME,
                        Condition.BETWEEN,
                        createToDateWithOffset(System.currentTimeMillis(), 1))
                .addDateTerm(MetaFields.EFFECTIVE_TIME,
                        Condition.BETWEEN,
                        createToDateWithOffset(System.currentTimeMillis(), 1))
                .addDateTerm(MetaFields.STATUS_TIME,
                        Condition.BETWEEN,
                        createToDateWithOffset(System.currentTimeMillis(), 1))
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED1)
                .addIdTerm(MetaFields.PARENT_ID, Condition.EQUALS, 1)
                .addIdTerm(MetaFields.ID, Condition.EQUALS, 1)
//                .addTerm(StreamDataSource.PIPELINE, Condition.EQUALS, "1")
//                .addTerm(StreamDataSource.STREAM_PROCESSOR_ID, Condition.EQUALS, "1")
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(expression);
        findMetaCriteria.setPageRequest(PageRequest.createDefault());
        findMetaCriteria.setSort(MetaFields.CREATE_TIME.getFldName());
//        findStreamCriteria.setStreamIdRange(new IdRange(0L, 1L));

        assertThat(metaService.find(findMetaCriteria).size()).isEqualTo(0L);
    }

    @Test
    void testBasicImportExportList() throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();

        Meta exactMetaData;
        try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
            streamTarget.getMeta().getFeedName();
            exactMetaData = streamTarget.getMeta();
            TargetUtil.write(streamTarget, testString);
        }

        // Refresh
        try (final Source streamSource = streamStore.openSource(exactMetaData.getId())) {
            exactMetaData = streamSource.getMeta();

            assertThat(exactMetaData.getStatus()).isSameAs(Status.UNLOCKED);

            // Check we can read it back in

            assertThat(streamSource).isNotNull();
            // Must be a proxy
            assertThat(streamSource.getMeta().getFeedName()).isNotNull();
        }

        final List<Meta> list = metaService.find(FindMetaCriteria.createWithType(StreamTypeNames.RAW_EVENTS))
                .getValues();

        boolean foundOne = false;
        for (final Meta result : list) {
            assertThat(pathHelper.getRootPath(Paths.get(""),
                    result,
                    StreamTypeNames.RAW_EVENTS)).isNotNull();
            assertThat(pathHelper.getBaseName(result)).isNotNull();
            if (pathHelper.getBaseName(result)
                    .equals(pathHelper.getBaseName(exactMetaData))) {
                foundOne = true;
            }
        }

        assertThat(foundOne).as("Expecting to find at least that one file " + exactMetaData).isTrue();

        assertThat(metaService.find(new FindMetaCriteria()).size() >= 1).as(
                "Expecting to find at least 1 with no criteria").isTrue();

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
        assertThat(metaService.find(new FindMetaCriteria(expression)).size() >= 1).as(
                "Expecting to find at least 1 with UNLOCKED criteria").isTrue();

        final FindDataVolumeCriteria volumeCriteria = FindDataVolumeCriteria.create(exactMetaData);
        assertThat(dataVolumeService.find(volumeCriteria).size() >= 1).as(
                "Expecting to find at least 1 with day old criteria").isTrue();
    }

    private void doTestDeleteSource(final DeleteTestStyle style) throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();
        final Meta meta;
        try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
            meta = streamTarget.getMeta();
            TargetUtil.write(streamTarget, testString);
        }

        if (DeleteTestStyle.META.equals(style)) {
            try (final Source streamSource = streamStore.openSource(meta.getId())) {
                assertThat(streamSource).isNotNull();
            }
            // This should delete it
            metaService.delete(meta.getId());
        } else if (DeleteTestStyle.OPEN.equals(style)) {
            try (final Source streamSource = streamStore.openSource(meta.getId())) {
                assertThat(streamSource).isNotNull();
                metaService.delete(streamSource.getMeta().getId());
            }
        } else if (DeleteTestStyle.OPEN_TOUCHED_CLOSED.equals(style)) {
            try (final Source streamSource = streamStore.openSource(meta.getId())) {
                assertThat(streamSource).isNotNull();
                try (final InputStreamProvider inputStreamProvider = streamSource.get(0)) {
                    try (final InputStream inputStream = inputStreamProvider.get()) {
                        inputStream.read();
                    }
                }
                metaService.delete(streamSource.getMeta().getId());
            }
        }

        try (final Source streamSource = streamStore.openSource(meta.getId(), true)) {
            assertThat(streamSource.getMeta().getStatus()).isEqualTo(Status.DELETED);
        }
    }

    private void doTestDeleteTarget(final DeleteTestStyle style) throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();

        Meta meta = null;

        if (DeleteTestStyle.META.equals(style)) {
            try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
                meta = streamTarget.getMeta();
            }
            // This should delete it
            metaService.delete(meta.getId());
        } else if (DeleteTestStyle.OPEN.equals(style)) {
            try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
                meta = streamTarget.getMeta();
                streamStore.deleteTarget(streamTarget);
            }
        } else if (DeleteTestStyle.OPEN_TOUCHED.equals(style)) {
            try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
                meta = streamTarget.getMeta();
                TargetUtil.write(streamTarget, testString);
                streamStore.deleteTarget(streamTarget);
            }
        } else if (DeleteTestStyle.OPEN_TOUCHED_CLOSED.equals(style)) {
            try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
                meta = streamTarget.getMeta();
                TargetUtil.write(streamTarget, testString);
                streamStore.deleteTarget(streamTarget);
            }
        }

        try (final Source streamSource = streamStore.openSource(meta.getId(), true)) {
            assertThat(streamSource.getMeta().getStatus()).isEqualTo(Status.DELETED);
        }
    }

    @Test
    void testDelete1() throws IOException {
        doTestDeleteSource(DeleteTestStyle.META);
    }

    @Test
    void testDelete2() throws IOException {
        doTestDeleteSource(DeleteTestStyle.OPEN);
    }

    @Test
    void testDelete4() throws IOException {
        doTestDeleteTarget(DeleteTestStyle.META);
    }

    @Test
    void testDelete5() throws IOException {
        doTestDeleteTarget(DeleteTestStyle.OPEN);
    }

    @Test
    void testDelete6() throws IOException {
        doTestDeleteTarget(DeleteTestStyle.OPEN_TOUCHED);
    }

    @Test
    void testDelete7() throws IOException {
        doTestDeleteSource(DeleteTestStyle.OPEN_TOUCHED_CLOSED);
    }

    @Test
    void testDelete8() throws IOException {
        doTestDeleteTarget(DeleteTestStyle.OPEN_TOUCHED_CLOSED);
    }

    @Test
    void testFileSystem() throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();

        final Meta exactMetaData;
        try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
            exactMetaData = streamTarget.getMeta();
            TargetUtil.write(streamTarget, testString);
            streamTarget.getAttributes().put(MetaFields.REC_READ.getFldName(), "10");
            streamTarget.getAttributes().put(MetaFields.REC_WRITE.getFldName(), "20");
        }

        final Meta reloadMetaData = metaService.find(FindMetaCriteria.createFromMeta(exactMetaData)).getFirst();

        try (final Source streamSource = streamStore.openSource(reloadMetaData.getId())) {
            try (final InputStreamProvider inputStreamProvider = streamSource.get(0)) {
                try (final InputStream is = inputStreamProvider.get()) {
                    assertThat(is).isNotNull();
                }
            }
        }

        final FindMetaCriteria criteria = FindMetaCriteria.createFromMeta(reloadMetaData);

//        streamAttributeValueFlush.flush();
        final MetaRow metaRow = metaService.findRows(criteria).getFirst();

        assertThat(metaRow.getAttributeValue(MetaFields.REC_READ.getFldName())).isEqualTo("10");
        assertThat(metaRow.getAttributeValue(MetaFields.REC_WRITE.getFldName())).isEqualTo("20");
    }

    @Test
    void testWriteNothing() throws IOException {
        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();

        final Meta exactMetaData;
        try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
            exactMetaData = streamTarget.getMeta();
        }

        final Meta reloadMetaData = metaService.find(FindMetaCriteria.createFromMeta(exactMetaData)).getFirst();

        try (final Source streamSource = streamStore.openSource(reloadMetaData.getId())) {
            try (final InputStreamProvider inputStreamProvider = streamSource.get(0)) {
                try (final InputStream is = inputStreamProvider.get()) {
                    assertThat(is).isNotNull();
                }
            }
        }
    }

    @Test
    void testEffective() throws IOException {
        final String feed1 = FileSystemTestUtil.getUniqueTestString();
        final String feed2 = FileSystemTestUtil.getUniqueTestString();
        final String feed3 = FileSystemTestUtil.getUniqueTestString();

//        setupFeed(feed1);
        final Meta refData1 = buildRefData(feed2, 2008, 2, StreamTypeNames.REFERENCE, false);
        final Meta refData2 = buildRefData(feed2, 2009, 2, StreamTypeNames.REFERENCE, false);
        final Meta refData3 = buildRefData(feed2, 2010, 2, StreamTypeNames.REFERENCE, false);

        // These 2 should get ignored as one is locked and the other is RAW
        final Set<Long> invalidFeeds = new HashSet<>();
        invalidFeeds.add(buildRefData(feed2, 2010, 2, StreamTypeNames.REFERENCE, true).getId());

        invalidFeeds.add(buildRefData(feed2, 2010, 2, StreamTypeNames.RAW_REFERENCE, false).getId());

        // Build some for another feed.
        buildRefData(feed3, 2008, 2, StreamTypeNames.REFERENCE, false);
        buildRefData(feed3, 2009, 2, StreamTypeNames.REFERENCE, false);
        buildRefData(feed3, 2010, 2, StreamTypeNames.REFERENCE, false);
        buildRefData(feed3, 2011, 2, StreamTypeNames.REFERENCE, false);

        // 2009 to 2010
        EffectiveMetaDataCriteria criteria = new EffectiveMetaDataCriteria(
                new Period(DateUtil.parseNormalDateTimeString("2009-01-01T00:00:00.000Z"),
                        DateUtil.parseNormalDateTimeString("2010-01-01T00:00:00.000Z")),
                feed2,
                StreamTypeNames.REFERENCE);

        EffectiveMetaSet list = metaService.findEffectiveData(criteria);

        // Make sure the list contains what it should.
        verifySet(list, refData1, refData2);

        // Try another test that picks up no tom within period but it should get
        // the last one as it would be the most effective.
        criteria = new EffectiveMetaDataCriteria(
                new Period(DateUtil.parseNormalDateTimeString("2013-01-01T00:00:00.000Z"),
                        DateUtil.parseNormalDateTimeString("2014-01-01T00:00:00.000Z")),
                feed2,
                StreamTypeNames.REFERENCE);

        list = metaService.findEffectiveData(criteria);

        // Make sure the list contains what it should.
        verifySet(list, refData3);

        assertThat(invalidFeeds.contains(list.iterator().next().getId())).isFalse();
    }

    /**
     * Check that the list of stream contains the items we expect.
     *
     * @param list
     * @param expected
     */
    private void verifySet(final EffectiveMetaSet list, final Meta... expected) {
        assertThat(list)
                .isNotNull();
        assertThat(list.size())
                .isEqualTo(expected.length);
        final Set<EffectiveMeta> expectedSet = Arrays.stream(expected)
                .map(EffectiveMeta::new)
                .collect(Collectors.toSet());

        assertThat(list)
                .containsAll(expectedSet);
    }

    private Meta buildRefData(final String feed,
                              final int year,
                              final int month,
                              final String type,
                              final boolean lock)
            throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(feed)
                .typeName(type)
                .effectiveMs(
                        ZonedDateTime.of(year, month, N1, N13, 0, 0, 0, ZoneOffset.UTC)
                                .toInstant()
                                .toEpochMilli())
                .build();

        final Target streamTarget = streamStore.openTarget(metaProperties);
        TargetUtil.write(streamTarget, testString);
//        streamTarget.close();
        // Leave locked ?
        if (!lock) {
            streamTarget.close();
        }
        // commonTestControl.clearContext();

        return streamTarget.getMeta();
    }

    @Test
    void testDeleteStreamTarget() throws IOException {
        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();

        final String testString = FileSystemTestUtil.getUniqueTestString();

        final Meta meta;
        final Target t;
        try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
            meta = streamTarget.getMeta();
            t = streamTarget;
            TargetUtil.write(streamTarget, testString);
            streamStore.deleteTarget(t);
        }

        // We shouldn't be able to close a stream target again.
        assertThatThrownBy(t::close).isInstanceOf(RuntimeException.class);

        Meta reloadedMeta = metaService.find(FindMetaCriteria.createFromMeta(meta)).getFirst();
        assertThat(reloadedMeta).isNull();

        streamStore.deleteTarget(t);

        reloadedMeta = metaService.find(FindMetaCriteria.createFromMeta(meta)).getFirst();
        assertThat(reloadedMeta).isNull();
    }

    @Test
    void testAppendStream() throws IOException {
        final String testString1 = FileSystemTestUtil.getUniqueTestString();
        final String testString2 = FileSystemTestUtil.getUniqueTestString();
        final String testString3 = FileSystemTestUtil.getUniqueTestString();
        final String testString4 = FileSystemTestUtil.getUniqueTestString();
        final String testString5 = FileSystemTestUtil.getUniqueTestString();
        final String testString6 = FileSystemTestUtil.getUniqueTestString();

        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();

        final Meta meta;
        try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
            meta = streamTarget.getMeta();
            TargetUtil.write(streamTarget, "xyz");
            streamTarget.getAttributes().put(testString1, testString2);
            streamTarget.getAttributes().put(MetaFields.REC_READ.getFldName(), "100");
        }

        final DataVolume dataVolume = dataVolumeService.findDataVolume(meta.getId());
//        final Path volumePath = Paths.get(dataVolume.getVolumePath());
//        final Path rootFile = pathHelper.getRootPath(volumePath, meta, StreamTypeNames.RAW_EVENTS);
//
//        assertThat(Files.isRegularFile(rootFile)).isTrue();
//
//        try (final Source streamSource = streamStore.openSource(meta.getId())) {
//            meta = streamSource.getMeta();
//            assertThat(streamSource.getAttributes().get(testString1)).isEqualTo(testString2);
//        }
//
//        final Path manifestFile = pathHelper.getChildPath(rootFile, InternalStreamTypeNames.MANIFEST);
//
//        assertThat(Files.isRegularFile(manifestFile)).isTrue();
//
//        try (final Target streamTarget = streamStore.openExistingTarget(meta)) {
//            meta = streamTarget.getMeta();
//            streamTarget.getAttributes().put(testString3, testString4);
//        }
//
//        try (final Source streamSource = streamStore.openSource(meta.getId())) {
//            meta = streamSource.getMeta();
//            assertThat(streamSource.getAttributes().get(testString1)).isEqualTo(testString2);
//            assertThat(streamSource.getAttributes().get(testString3)).isEqualTo(testString4);
//        }
//
//        assertThat(FileSystemUtil.deleteAnyPath(manifestFile)).isTrue();
////        for (final StreamAttributeValue value : streamAttributeValueService
////                .find(FindStreamAttributeValueCriteria.create(stream))) {
////            assertThat(streamAttributeValueService.delete(value)).isTrue();
////        }
//
//        try (final Target streamTarget = streamStore.openExistingTarget(meta)) {
//            meta = streamTarget.getMeta();
//            streamTarget.getAttributes().put(testString5, testString6);
//            assertThat(streamTarget.getAttributes().get(testString3)).isNull();
//        }
//
//        try (final Source streamSource = streamStore.openSource(meta.getId())) {
//            meta = streamSource.getMeta();
//            assertThat(streamSource.getAttributes().get(testString5)).isEqualTo(testString6);
//            assertThat(streamSource.getAttributes().get(testString3)).isNull();
//        }
    }

    /**
     * Test.
     */
    @Test
    void testIOErrors() throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();

        Path dir = null;
        try {
            try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
                TargetUtil.write(streamTarget, testString);

                dir = ((FsTarget) streamTarget).getFile().getParent();
                FileUtil.removeFilePermission(dir,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.GROUP_WRITE,
                        PosixFilePermission.OTHERS_WRITE);
            }

            fail("Expecting an error");
        } catch (final RuntimeException e) {
            // Expected.
        } finally {
            FileUtil.addFilePermission(dir,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_WRITE,
                    PosixFilePermission.OTHERS_WRITE);
        }
    }

    private enum DeleteTestStyle {
        META,
        OPEN,
        OPEN_TOUCHED,
        OPEN_TOUCHED_CLOSED
    }
}
