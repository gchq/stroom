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
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocRefs;
import stroom.explorer.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.importexport.ImportExportSerializer;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.query.api.v2.DocRef;
import stroom.statistics.shared.StatisticStore;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticType;
import stroom.statistics.shared.StatisticsDataSourceData;
import stroom.statistics.shared.common.StatisticField;
import stroom.statistics.sql.datasource.FindStatisticsEntityCriteria;
import stroom.statistics.sql.datasource.StatisticStoreEntityService;
import stroom.statistics.sql.datasource.StatisticsDataSourceProvider;
import stroom.streamstore.fs.FileSystemUtil;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.FileUtil;

import javax.inject.Inject;
import java.nio.file.Path;

public class TestStatisticsDataSourceImportExportSerializer extends AbstractCoreIntegrationTest {
    @Inject
    private ImportExportSerializer importExportSerializer;
    @Inject
    private StatisticStoreEntityService statisticsDataSourceService;
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
        final DocRef docRef = explorerService.create(StatisticStoreEntity.ENTITY_TYPE,"StatName1", null, null);
        final StatisticStoreEntity statisticsDataSource = statisticsDataSourceService.readDocument(docRef);
        statisticsDataSource.setDescription("My Description");
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setStatisticDataSourceDataObject(new StatisticsDataSourceData());
        statisticsDataSource.getStatisticDataSourceDataObject().addStatisticField(new StatisticField("tag1"));
        statisticsDataSource.getStatisticDataSourceDataObject().addStatisticField(new StatisticField("tag2"));
        statisticsDataSourceService.save(statisticsDataSource);

        Assert.assertEquals(1, statisticsDataSourceService.find(FindStatisticsEntityCriteria.instance()).size());

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        FileSystemUtil.mkdirs(null, testDataDir);

        importExportSerializer.write(testDataDir, buildFindFolderCriteria(), true, null);

        Assert.assertEquals(3, FileUtil.count(testDataDir));

        // now clear out the java entities and import from file
        clean(true);

        Assert.assertEquals(0, statisticsDataSourceService.find(FindStatisticsEntityCriteria.instance()).size());

        importExportSerializer.read(testDataDir, null, ImportMode.IGNORE_CONFIRMATION);

        final BaseResultList<StatisticStoreEntity> dataSources = statisticsDataSourceService
                .find(FindStatisticsEntityCriteria.instance());

        Assert.assertEquals(1, dataSources.size());

        final StatisticStoreEntity importedDataSource = dataSources.get(0);

        Assert.assertEquals(statisticsDataSource.getName(), importedDataSource.getName());
        Assert.assertEquals(statisticsDataSource.getStatisticType(), importedDataSource.getStatisticType());
        Assert.assertEquals(statisticsDataSource.getDescription(), importedDataSource.getDescription());

        Assert.assertEquals(statisticsDataSource.getStatisticDataSourceDataObject(),
                importedDataSource.getStatisticDataSourceDataObject());
    }

    @Test
    public void testDeSerialiseOnLoad() {
        final StatisticStoreEntity statisticsDataSource = statisticsDataSourceService.create("StatName1");
        statisticsDataSource.setDescription("My Description");
        statisticsDataSource.setStatisticType(StatisticType.COUNT);

        statisticsDataSource.setStatisticDataSourceDataObject(new StatisticsDataSourceData());
        statisticsDataSource.getStatisticDataSourceDataObject().addStatisticField(new StatisticField("tag1"));
        statisticsDataSource.getStatisticDataSourceDataObject().addStatisticField(new StatisticField("tag2"));

        statisticsDataSourceService.save(statisticsDataSource);

        StatisticStoreEntity statisticsDataSource2 = statisticsDataSourceService
                .find(FindStatisticsEntityCriteria.instance()).getFirst();
        Assert.assertNotNull(statisticsDataSource2);

        final String uuid = statisticsDataSource2.getUuid();

        final StatisticStoreEntity statisticsDataSource3 = statisticsDataSourceService.loadByUuid(uuid);

        // Assert.assertNotNull(((StatisticsDataSource)
        // statisticsDataSource3).getStatisticDataSourceData());
        Assert.assertNotNull(statisticsDataSource3);
        Assert.assertNotNull(statisticsDataSource3.getStatisticDataSourceDataObject());

        DocRef statisticDataSource3DocRef = DocRefUtil.create(statisticsDataSource3);

        final DataSource dataSource = statisticsDataSourceProvider.getDataSource(statisticDataSource3DocRef);

        Assert.assertNotNull(dataSource.getFields());
    }
}
