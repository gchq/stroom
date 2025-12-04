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

package stroom.statistics.impl.sql;


import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.api.ImportExportSerializer;
import stroom.importexport.api.ImportExportVersion;
import stroom.importexport.shared.ImportSettings;
import stroom.query.common.v2.ResultStoreManager;
import stroom.statistics.impl.sql.entity.StatisticStoreStore;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticStore;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.util.io.FileUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestStatisticsDataSourceImportExportSerializer extends AbstractCoreIntegrationTest {

    @Inject
    private ImportExportSerializer importExportSerializer;
    @Inject
    private StatisticStoreStore statisticStoreStore;
    @Inject
    private ResultStoreManager searchResponseCreatorManager;
    @Inject
    private ExplorerService explorerService;
    @Inject
    private CommonTestControl commonTestControl;

    private Set<DocRef> buildFindFolderCriteria() {
        final Set<DocRef> docRefs = new HashSet<>();
        docRefs.add(ExplorerConstants.SYSTEM_DOC_REF);
        return docRefs;
    }

    /**
     * Create a populated {@link StatisticStore} object, serialise it to file,
     * de-serialise it back to an object then compare the first object with the
     * second one
     */
    @Test
    void testStatisticsDataSource() {
        final ExplorerNode statNode = explorerService.create(StatisticStoreDoc.TYPE, "StatName1", null, null);
        final StatisticStoreDoc statisticsDataSource = statisticStoreStore.readDocument(statNode.getDocRef());
        statisticsDataSource.setDescription("My Description");
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setConfig(new StatisticsDataSourceData());
        statisticsDataSource.getConfig().addStatisticField(new StatisticField("tag1"));
        statisticsDataSource.getConfig().addStatisticField(new StatisticField("tag2"));
        statisticStoreStore.writeDocument(statisticsDataSource);

        assertThat(statisticStoreStore.list().size()).isEqualTo(1);

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        FileUtil.mkdirs(testDataDir);

        importExportSerializer.write(
                null,
                testDataDir,
                buildFindFolderCriteria(),
                Collections.emptySet(),
                true,
                ImportExportVersion.V1);

        assertThat(FileUtil.count(testDataDir)).isEqualTo(2);

        // now clear out the java entities and import from file
        commonTestControl.clear();

        assertThat(statisticStoreStore.list().size()).isEqualTo(0);

        importExportSerializer.read(
                testDataDir,
                null,
                ImportSettings.auto());

        final List<DocRef> dataSources = statisticStoreStore.list();

        assertThat(dataSources.size()).isEqualTo(1);

        final StatisticStoreDoc importedDataSource = statisticStoreStore.readDocument(dataSources.get(0));

        assertThat(importedDataSource.getName()).isEqualTo(statisticsDataSource.getName());
        assertThat(importedDataSource.getStatisticType()).isEqualTo(statisticsDataSource.getStatisticType());
        assertThat(importedDataSource.getDescription()).isEqualTo(statisticsDataSource.getDescription());

        assertThat(importedDataSource.getConfig()).isEqualTo(statisticsDataSource.getConfig());
    }

    /**
     * Create a populated {@link StatisticStore} object, serialise it to file,
     * de-serialise it back to an object then compare the first object with the
     * second one
     */
    @Test
    void testStatisticsDataSourceV2() {
        final ExplorerNode statNode = explorerService.create(StatisticStoreDoc.TYPE, "StatName1", null, null);
        final StatisticStoreDoc statisticsDataSource = statisticStoreStore.readDocument(statNode.getDocRef());
        statisticsDataSource.setDescription("My Description");
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setConfig(new StatisticsDataSourceData());
        statisticsDataSource.getConfig().addStatisticField(new StatisticField("tag1"));
        statisticsDataSource.getConfig().addStatisticField(new StatisticField("tag2"));
        statisticStoreStore.writeDocument(statisticsDataSource);

        assertThat(statisticStoreStore.list().size()).isEqualTo(1);

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        FileUtil.mkdirs(testDataDir);

        importExportSerializer.write(
                null,
                testDataDir,
                buildFindFolderCriteria(),
                Collections.emptySet(),
                true,
                ImportExportVersion.V2);

        assertThat(FileUtil.count(testDataDir)).isEqualTo(2);

        // now clear out the java entities and import from file
        commonTestControl.clear();

        assertThat(statisticStoreStore.list().size()).isEqualTo(0);

        importExportSerializer.read(
                testDataDir,
                null,
                ImportSettings.auto());

        final List<DocRef> dataSources = statisticStoreStore.list();

        assertThat(dataSources.size()).isEqualTo(1);

        final StatisticStoreDoc importedDataSource = statisticStoreStore.readDocument(dataSources.get(0));

        assertThat(importedDataSource.getName()).isEqualTo(statisticsDataSource.getName());
        assertThat(importedDataSource.getStatisticType()).isEqualTo(statisticsDataSource.getStatisticType());
        assertThat(importedDataSource.getDescription()).isEqualTo(statisticsDataSource.getDescription());

        assertThat(importedDataSource.getConfig()).isEqualTo(statisticsDataSource.getConfig());
    }

    @Test
    void testDeSerialiseOnLoad() {
        final DocRef docRef = statisticStoreStore.createDocument("StatName1");
        final StatisticStoreDoc statisticsDataSource = statisticStoreStore.readDocument(docRef);
        statisticsDataSource.setDescription("My Description");
        statisticsDataSource.setStatisticType(StatisticType.COUNT);

        statisticsDataSource.setConfig(new StatisticsDataSourceData());
        statisticsDataSource.getConfig().addStatisticField(new StatisticField("tag1"));
        statisticsDataSource.getConfig().addStatisticField(new StatisticField("tag2"));

        statisticStoreStore.writeDocument(statisticsDataSource);

        final DocRef statisticStoreRef = statisticStoreStore.list().get(0);
        final StatisticStoreDoc statisticsDataSource2 = statisticStoreStore.readDocument(statisticStoreRef);
        assertThat(statisticsDataSource2).isNotNull();

        final StatisticStoreDoc statisticsDataSource3 = statisticStoreStore.readDocument(statisticStoreRef);

        // assertThat(((StatisticsDataSource)
        // statisticsDataSource3).getStatisticDataSourceData()).isNotNull();
        assertThat(statisticsDataSource3).isNotNull();
        assertThat(statisticsDataSource3.getConfig()).isNotNull();

        final DocRef statisticDataSource3DocRef = DocRefUtil.create(statisticsDataSource3);
    }
}
