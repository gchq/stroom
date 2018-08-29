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

package stroom.statistics.common;

import org.junit.Assert;
import org.junit.Test;
import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.entity.shared.DocRefs;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.importexport.ImportExportSerializer;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.statistics.shared.StatisticStore;
import stroom.statistics.shared.StatisticStoreDoc;
import stroom.statistics.shared.StatisticType;
import stroom.statistics.shared.StatisticsDataSourceData;
import stroom.statistics.shared.common.StatisticField;
import stroom.statistics.sql.entity.StatisticStoreStore;
import stroom.statistics.sql.entity.StatisticsDataSourceProvider;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.FileUtil;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.List;

public class TestStatisticsDataSourceImportExportSerializer extends AbstractCoreIntegrationTest {
    @Inject
    private ImportExportSerializer importExportSerializer;
    @Inject
    private StatisticStoreStore statisticStoreStore;
    @Inject
    private StatisticsDataSourceProvider statisticsDataSourceProvider;
    @Inject
    private ExplorerService explorerService;

    private DocRefs buildFindFolderCriteria() {
        final DocRefs docRefs = new DocRefs();
        docRefs.add(ExplorerConstants.ROOT_DOC_REF);
        return docRefs;
    }

    /**
     * Create a populated {@link StatisticStore} object, serialise it to file,
     * de-serialise it back to an object then compare the first object with the
     * second one
     */
    @Test
    public void testStatisticsDataSource() {
        final DocRef docRef = explorerService.create(StatisticStoreDoc.DOCUMENT_TYPE, "StatName1", null, null);
        final StatisticStoreDoc statisticsDataSource = statisticStoreStore.readDocument(docRef);
        statisticsDataSource.setDescription("My Description");
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setConfig(new StatisticsDataSourceData());
        statisticsDataSource.getConfig().addStatisticField(new StatisticField("tag1"));
        statisticsDataSource.getConfig().addStatisticField(new StatisticField("tag2"));
        statisticStoreStore.writeDocument(statisticsDataSource);

        Assert.assertEquals(1, statisticStoreStore.list().size());

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        FileUtil.mkdirs(testDataDir);

        importExportSerializer.write(testDataDir, buildFindFolderCriteria(), true, null);

        Assert.assertEquals(2, FileUtil.count(testDataDir));

        // now clear out the java entities and import from file
        clean(true);

        Assert.assertEquals(0, statisticStoreStore.list().size());

        importExportSerializer.read(testDataDir, null, ImportMode.IGNORE_CONFIRMATION);

        final List<DocRef> dataSources = statisticStoreStore.list();

        Assert.assertEquals(1, dataSources.size());

        final StatisticStoreDoc importedDataSource = statisticStoreStore.readDocument(dataSources.get(0));

        Assert.assertEquals(statisticsDataSource.getName(), importedDataSource.getName());
        Assert.assertEquals(statisticsDataSource.getStatisticType(), importedDataSource.getStatisticType());
        Assert.assertEquals(statisticsDataSource.getDescription(), importedDataSource.getDescription());

        Assert.assertEquals(statisticsDataSource.getConfig(),
                importedDataSource.getConfig());
    }

    @Test
    public void testDeSerialiseOnLoad() {
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
        Assert.assertNotNull(statisticsDataSource2);

        final StatisticStoreDoc statisticsDataSource3 = statisticStoreStore.readDocument(statisticStoreRef);

        // Assert.assertNotNull(((StatisticsDataSource)
        // statisticsDataSource3).getStatisticDataSourceData());
        Assert.assertNotNull(statisticsDataSource3);
        Assert.assertNotNull(statisticsDataSource3.getConfig());

        DocRef statisticDataSource3DocRef = DocRefUtil.create(statisticsDataSource3);

        final DataSource dataSource = statisticsDataSourceProvider.getDataSource(statisticDataSource3DocRef);

        Assert.assertNotNull(dataSource.getFields());
    }
}
