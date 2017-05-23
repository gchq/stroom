/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.statistics.common;

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.datasource.api.v1.DataSource;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.entity.shared.ImportState.ImportMode;
import stroom.importexport.server.ImportExportSerializer;
import stroom.query.api.v1.DocRef;
import stroom.statistics.server.common.StatisticsDataSourceProvider;
import stroom.statistics.shared.common.StatisticField;
import stroom.statistics.shared.StatisticStore;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticType;
import stroom.statistics.shared.StatisticsDataSourceData;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.util.test.FileSystemTestUtil;

import javax.annotation.Resource;
import java.io.File;

public class TestStatisticsDataSourceImportExportSerializer extends AbstractCoreIntegrationTest {
    @Resource
    private ImportExportSerializer importExportSerializer;
    @Resource
    private FolderService folderService;
    @Resource
    private StatisticStoreEntityService statisticsDataSourceService;
    @Resource
    private StatisticsDataSourceProvider statisticsDataSourceProvider;

    private DocRefs buildFindFolderCriteria() {
        final DocRefs docRefs = new DocRefs();
        docRefs.add(new DocRef(Folder.ENTITY_TYPE,"0", "System"));
        return docRefs;
    }

    /**
     * Create a populated {@link StatisticStore} object, serialise it to file,
     * de-serialise it back to an object then compare the first object with the
     * second one
     */
    @Test
    public void testStatisticsDataSource() {
        final DocRef folder = DocRefUtil.create(folderService.create(null, FileSystemTestUtil.getUniqueTestString()));

        final StatisticStoreEntity statisticsDataSource = statisticsDataSourceService.create(folder, "StatName1");
        statisticsDataSource.setDescription("My Description");
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setStatisticDataSourceDataObject(new StatisticsDataSourceData());
        statisticsDataSource.getStatisticDataSourceDataObject().addStatisticField(new StatisticField("tag1"));
        statisticsDataSource.getStatisticDataSourceDataObject().addStatisticField(new StatisticField("tag2"));
        statisticsDataSourceService.save(statisticsDataSource);

        Assert.assertEquals(1, statisticsDataSourceService.find(FindStatisticsEntityCriteria.instance()).size());

        final File testDataDir = new File(getCurrentTestDir(), "ExportTest");

        FileSystemUtil.deleteDirectory(testDataDir);
        FileSystemUtil.mkdirs(null, testDataDir);

        importExportSerializer.write(testDataDir.toPath(), buildFindFolderCriteria(), true, null);

        Assert.assertEquals(2, testDataDir.listFiles().length);

        // now clear out the java entities and import from file
        clean(true);

        Assert.assertEquals(0, statisticsDataSourceService.find(FindStatisticsEntityCriteria.instance()).size());

        importExportSerializer.read(testDataDir.toPath(), null, ImportMode.IGNORE_CONFIRMATION);

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
        final DocRef folder = DocRefUtil.create(folderService.create(null, FileSystemTestUtil.getUniqueTestString()));

        final StatisticStoreEntity statisticsDataSource = statisticsDataSourceService.create(folder, "StatName1");
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
