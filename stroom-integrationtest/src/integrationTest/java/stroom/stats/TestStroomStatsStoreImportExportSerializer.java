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

package stroom.stats;

import org.junit.Assert;
import org.junit.Test;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.ImportState;
import stroom.explorer.server.ExplorerService;
import stroom.importexport.server.ImportExportSerializer;
import stroom.query.api.v2.DocRef;
import stroom.statistics.server.sql.datasource.StatisticsDataSourceProvider;
import stroom.statistics.server.stroomstats.entity.FindStroomStatsStoreEntityCriteria;
import stroom.statistics.server.stroomstats.entity.StroomStatsStoreEntityService;
import stroom.statistics.shared.StatisticStore;
import stroom.statistics.shared.StatisticType;
import stroom.stats.shared.StatisticField;
import stroom.stats.shared.StroomStatsStoreEntity;
import stroom.stats.shared.StroomStatsStoreEntityData;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.FileUtil;

import javax.annotation.Resource;
import java.nio.file.Path;

public class TestStroomStatsStoreImportExportSerializer extends AbstractCoreIntegrationTest {
    @Resource
    private ImportExportSerializer importExportSerializer;
    @Resource
    private StroomStatsStoreEntityService stroomStatsStoreEntityService;
    @Resource
    private StatisticsDataSourceProvider statisticsDataSourceProvider;
    @Resource
    private ExplorerService explorerService;

    private DocRefs buildFindFolderCriteria(DocRef folderDocRef) {
        final DocRefs docRefs = new DocRefs();
        docRefs.add(folderDocRef);
        return docRefs;
    }

    /**
     * Create a populated {@link StatisticStore} object, serialise it to file,
     * de-serialise it back to an object then compare the first object with the
     * second one
     */
    @Test
    public void testStatisticsDataSource() {
        final DocRef docRef = explorerService.create(StroomStatsStoreEntity.ENTITY_TYPE,"StatName1", null, null);
        final StroomStatsStoreEntity entity = stroomStatsStoreEntityService.readDocument(docRef);
        entity.setDescription("My Description");
        entity.setStatisticType(StatisticType.COUNT);
        entity.setDataObject(new StroomStatsStoreEntityData());
        entity.getDataObject().addStatisticField(new StatisticField("tag1"));
        entity.getDataObject().addStatisticField(new StatisticField("tag2"));
        stroomStatsStoreEntityService.save(entity);

        Assert.assertEquals(1, stroomStatsStoreEntityService.find(FindStroomStatsStoreEntityCriteria.instance()).size());

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileSystemUtil.deleteDirectory(testDataDir);
        FileSystemUtil.mkdirs(null, testDataDir);

        final DocRefs docRefs = new DocRefs();
        docRefs.add(docRef);
        importExportSerializer.write(testDataDir, docRefs, true, null);

        Assert.assertEquals(3, FileUtil.list(testDataDir).size());

        // now clear out the java entities and import from file
        clean(true);

        Assert.assertEquals(0, stroomStatsStoreEntityService.find(FindStroomStatsStoreEntityCriteria.instance()).size());

        importExportSerializer.read(testDataDir, null, ImportState.ImportMode.IGNORE_CONFIRMATION);

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