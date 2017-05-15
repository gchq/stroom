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

package stroom.stats;

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.entity.shared.*;
import stroom.importexport.server.ImportExportSerializer;
import stroom.query.api.DocRef;
import stroom.statistics.server.common.StatisticsDataSourceProvider;
import stroom.statistics.server.stroomstats.entity.FindStroomStatsStoreEntityCriteria;
import stroom.statistics.server.stroomstats.entity.StroomStatsStoreEntityService;
import stroom.statistics.shared.StatisticStore;
import stroom.statistics.shared.StatisticType;
import stroom.stats.shared.StatisticField;
import stroom.stats.shared.StroomStatsStoreEntity;
import stroom.stats.shared.StroomStatsStoreEntityData;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.util.test.FileSystemTestUtil;

import javax.annotation.Resource;
import java.io.File;

public class TestStroomStatsStoreImportExportSerializer extends AbstractCoreIntegrationTest {
    @Resource
    private ImportExportSerializer importExportSerializer;
    @Resource
    private FolderService folderService;
    @Resource
    private StroomStatsStoreEntityService stroomStatsStoreEntityService;
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

        final StroomStatsStoreEntity entity = stroomStatsStoreEntityService.create(folder, "StatName1");
        entity.setDescription("My Description");
        entity.setStatisticType(StatisticType.COUNT);
        entity.setDataObject(new StroomStatsStoreEntityData());
        entity.getDataObject().addStatisticField(new StatisticField("tag1"));
        entity.getDataObject().addStatisticField(new StatisticField("tag2"));
        stroomStatsStoreEntityService.save(entity);

        Assert.assertEquals(1, stroomStatsStoreEntityService.find(FindStroomStatsStoreEntityCriteria.instance()).size());

        final File testDataDir = new File(getCurrentTestDir(), "ExportTest");

        FileSystemUtil.deleteDirectory(testDataDir);
        FileSystemUtil.mkdirs(null, testDataDir);

        importExportSerializer.write(testDataDir.toPath(), buildFindFolderCriteria(), true, null);

        Assert.assertEquals(2, testDataDir.listFiles().length);

        // now clear out the java entities and import from file
        clean(true);

        Assert.assertEquals(0, stroomStatsStoreEntityService.find(FindStroomStatsStoreEntityCriteria.instance()).size());

        importExportSerializer.read(testDataDir.toPath(), null, ImportState.ImportMode.IGNORE_CONFIRMATION);

        final BaseResultList<StroomStatsStoreEntity> dataSources = stroomStatsStoreEntityService
                .find(FindStroomStatsStoreEntityCriteria.instance());

        Assert.assertEquals(1, dataSources.size());

        final StroomStatsStoreEntity importedDataSource = dataSources.get(0);

        Assert.assertEquals(entity.getName(), importedDataSource.getName());
        Assert.assertEquals(entity.getStatisticType(), importedDataSource.getStatisticType());
        Assert.assertEquals(entity.getDescription(), importedDataSource.getDescription());

        Assert.assertEquals(entity.getDataObject(), importedDataSource.getDataObject());
    }


}
