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

package stroom.statistics.impl.hbase;


import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerService;
import stroom.importexport.impl.ImportExportSerializer;
import stroom.importexport.shared.ImportSettings;
import stroom.statistics.impl.hbase.entity.StroomStatsStoreStore;
import stroom.statistics.impl.hbase.shared.StatisticField;
import stroom.statistics.impl.hbase.shared.StatisticType;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreEntityData;
import stroom.statistics.impl.sql.shared.StatisticStore;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.util.io.FileUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomStatsStoreImportExportSerializer extends AbstractCoreIntegrationTest {

    @Inject
    private ImportExportSerializer importExportSerializer;
    @Inject
    private StroomStatsStoreStore stroomStatsStoreStore;
    @Inject
    private ExplorerService explorerService;
    @Inject
    private CommonTestControl commonTestControl;

    /**
     * Create a populated {@link StatisticStore} object, serialise it to file,
     * de-serialise it back to an object then compare the first object with the
     * second one
     */
    @Test
    void testStatisticsDataSource() {
        StroomStatsStoreDoc doc = stroomStatsStoreStore.createDocument();
        doc.setName("StatName1");
        doc.setDescription("My Description");
        doc.setStatisticType(StatisticType.COUNT);
        doc.setConfig(new StroomStatsStoreEntityData());
        doc.getConfig().addStatisticField(new StatisticField("tag1"));
        doc.getConfig().addStatisticField(new StatisticField("tag2"));
        doc = stroomStatsStoreStore.writeDocument(doc);
        explorerService.create(doc.asDocRef(), null, null);

        assertThat(stroomStatsStoreStore.list().size()).isEqualTo(1);

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        FileUtil.mkdirs(testDataDir);

        importExportSerializer.write(testDataDir, Set.of(doc.asDocRef()), true);

        assertThat(FileUtil.count(testDataDir)).isEqualTo(2);

        // now clear out the java entities and import from file
        commonTestControl.clear();

        assertThat(stroomStatsStoreStore.list().size()).isEqualTo(0);

        importExportSerializer.read(testDataDir, null, ImportSettings.auto());

        final List<DocRef> dataSources = stroomStatsStoreStore.list();

        assertThat(dataSources.size()).isEqualTo(1);

        final StroomStatsStoreDoc importedDataSource = stroomStatsStoreStore.readDocument(dataSources.getFirst());

        assertThat(importedDataSource.getName()).isEqualTo(doc.getName());
        assertThat(importedDataSource.getStatisticType()).isEqualTo(doc.getStatisticType());
        assertThat(importedDataSource.getDescription()).isEqualTo(doc.getDescription());

        assertThat(importedDataSource.getConfig()).isEqualTo(doc.getConfig());
    }
}
