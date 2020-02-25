/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.statistics.impl.sql;


import org.junit.jupiter.api.Test;
import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.importexport.impl.ImportExportSerializer;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.statistics.impl.sql.entity.StatisticStoreStore;
import stroom.statistics.impl.sql.entity.StatisticsDataSourceProvider;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticStore;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.FileUtil;

import javax.inject.Inject;
import java.nio.file.Path;
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
    private StatisticsDataSourceProvider statisticsDataSourceProvider;
    @Inject
    private ExplorerService explorerService;

    private Set<DocRef> buildFindFolderCriteria() {
        final Set<DocRef> docRefs = new HashSet<>();
        docRefs.add(ExplorerConstants.ROOT_DOC_REF);
        return docRefs;
    }

    /**
     * Create a populated {@link StatisticStore} object, serialise it to file,
     * de-serialise it back to an object then compare the first object with the
     * second one
     */
    @Test
    void testStatisticsDataSource() {
        final DocRef docRef = explorerService.create(StatisticStoreDoc.DOCUMENT_TYPE, "StatName1", null, null);
        final StatisticStoreDoc statisticsDataSource = statisticStoreStore.readDocument(docRef);
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

        importExportSerializer.write(testDataDir, buildFindFolderCriteria(), true, null);

        assertThat(FileUtil.count(testDataDir)).isEqualTo(2);

        // now clear out the java entities and import from file
        clean(true);

        assertThat(statisticStoreStore.list().size()).isEqualTo(0);

        importExportSerializer.read(testDataDir, null, ImportMode.IGNORE_CONFIRMATION);

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

        DocRef statisticDataSource3DocRef = DocRefUtil.create(statisticsDataSource3);

        final DataSource dataSource = statisticsDataSourceProvider.getDataSource(statisticDataSource3DocRef);

        assertThat(dataSource.getFields()).isNotNull();
    }
}
