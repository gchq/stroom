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
import stroom.docref.DocRef;
import stroom.entity.shared.DocRefs;
import stroom.explorer.ExplorerService;
import stroom.importexport.ImportExportSerializer;
import stroom.importexport.shared.ImportState;
import stroom.statistics.shared.StatisticStore;
import stroom.statistics.shared.StatisticType;
import stroom.statistics.sql.entity.StatisticsDataSourceProvider;
import stroom.statistics.stroomstats.entity.StroomStatsStoreStore;
import stroom.stats.shared.StatisticField;
import stroom.stats.shared.StroomStatsStoreDoc;
import stroom.stats.shared.StroomStatsStoreEntityData;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.FileUtil;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.List;

public class TestStroomStatsStoreImportExportSerializer extends AbstractCoreIntegrationTest {
    @Inject
    private ImportExportSerializer importExportSerializer;
    @Inject
    private StroomStatsStoreStore stroomStatsStoreStore;
    @Inject
    private StatisticsDataSourceProvider statisticsDataSourceProvider;
    @Inject
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
        final DocRef docRef = explorerService.create(StroomStatsStoreDoc.DOCUMENT_TYPE, "StatName1", null, null);
        final StroomStatsStoreDoc entity = stroomStatsStoreStore.readDocument(docRef);
        entity.setDescription("My Description");
        entity.setStatisticType(StatisticType.COUNT);
        entity.setConfig(new StroomStatsStoreEntityData());
        entity.getConfig().addStatisticField(new StatisticField("tag1"));
        entity.getConfig().addStatisticField(new StatisticField("tag2"));
        stroomStatsStoreStore.writeDocument(entity);

        Assert.assertEquals(1, stroomStatsStoreStore.list().size());

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        FileUtil.mkdirs(testDataDir);

        final DocRefs docRefs = new DocRefs();
        docRefs.add(docRef);
        importExportSerializer.write(testDataDir, docRefs, true, null);

        Assert.assertEquals(2, FileUtil.count(testDataDir));

        // now clear out the java entities and import from file
        clean(true);

        Assert.assertEquals(0, stroomStatsStoreStore.list().size());

        importExportSerializer.read(testDataDir, null, ImportState.ImportMode.IGNORE_CONFIRMATION);

        final List<DocRef> dataSources = stroomStatsStoreStore.list();

        Assert.assertEquals(1, dataSources.size());

        final StroomStatsStoreDoc importedDataSource = stroomStatsStoreStore.readDocument(dataSources.get(0));

        Assert.assertEquals(entity.getName(), importedDataSource.getName());
        Assert.assertEquals(entity.getStatisticType(), importedDataSource.getStatisticType());
        Assert.assertEquals(entity.getDescription(), importedDataSource.getDescription());

        Assert.assertEquals(entity.getConfig(), importedDataSource.getConfig());
    }
}