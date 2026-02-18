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

package stroom.statistics.impl.hbase;


import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.api.ImportExportSerializer;
import stroom.importexport.api.ImportExportVersion;
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
import java.util.Collections;
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
        final ExplorerNode statNode = explorerService.create(StroomStatsStoreDoc.TYPE,
                "StatName1",
                null,
                null);
        final StroomStatsStoreDoc entity = stroomStatsStoreStore.readDocument(statNode.getDocRef())
                .copy()
                .description("My Description")
                .statisticType(StatisticType.COUNT)
                .config(StroomStatsStoreEntityData
                        .builder()
                        .fields(List.of(
                                new StatisticField("tag1"),
                                new StatisticField("tag2")))
                        .build())
                .build();
        stroomStatsStoreStore.writeDocument(entity);

        assertThat(stroomStatsStoreStore.list().size()).isEqualTo(1);

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        FileUtil.mkdirs(testDataDir);

        importExportSerializer.write(
                null,
                testDataDir,
                Set.of(statNode.getDocRef()),
                Collections.emptySet(),
                true,
                ImportExportVersion.V1);

        assertThat(FileUtil.count(testDataDir)).isEqualTo(2);

        // now clear out the java entities and import from file
        commonTestControl.clear();

        assertThat(stroomStatsStoreStore.list().size()).isEqualTo(0);

        importExportSerializer.read(
                testDataDir,
                null,
                ImportSettings.auto());

        final List<DocRef> dataSources = stroomStatsStoreStore.list();

        assertThat(dataSources.size()).isEqualTo(1);

        final StroomStatsStoreDoc importedDataSource = stroomStatsStoreStore.readDocument(dataSources.getFirst());

        assertThat(importedDataSource.getName()).isEqualTo(entity.getName());
        assertThat(importedDataSource.getStatisticType()).isEqualTo(entity.getStatisticType());
        assertThat(importedDataSource.getDescription()).isEqualTo(entity.getDescription());

        assertThat(importedDataSource.getConfig()).isEqualTo(entity.getConfig());
    }

    /**
     * Create a populated {@link StatisticStore} object, serialise it to file,
     * de-serialise it back to an object then compare the first object with the
     * second one
     */
    @Test
    void testStatisticsDataSourceV2() {
        final ExplorerNode statNode = explorerService.create(StroomStatsStoreDoc.TYPE,
                "StatName1",
                null,
                null);
        final StroomStatsStoreDoc entity = stroomStatsStoreStore.readDocument(statNode.getDocRef())
                .copy()
                .description("My Description")
                .statisticType(StatisticType.COUNT)
                .config(StroomStatsStoreEntityData
                        .builder()
                        .fields(List.of(
                                new StatisticField("tag1"),
                                new StatisticField("tag2")))
                        .build())
                .build();
        stroomStatsStoreStore.writeDocument(entity);

        assertThat(stroomStatsStoreStore.list().size()).isEqualTo(1);

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        FileUtil.mkdirs(testDataDir);

        importExportSerializer.write(
                null,
                testDataDir,
                Set.of(statNode.getDocRef()),
                Collections.emptySet(),
                true,
                ImportExportVersion.V2);

        assertThat(FileUtil.count(testDataDir)).isEqualTo(2);

        // now clear out the java entities and import from file
        commonTestControl.clear();

        assertThat(stroomStatsStoreStore.list().size()).isEqualTo(0);

        importExportSerializer.read(
                testDataDir,
                null,
                ImportSettings.auto());

        final List<DocRef> dataSources = stroomStatsStoreStore.list();

        assertThat(dataSources.size()).isEqualTo(1);

        final StroomStatsStoreDoc importedDataSource = stroomStatsStoreStore.readDocument(dataSources.getFirst());

        assertThat(importedDataSource.getName()).isEqualTo(entity.getName());
        assertThat(importedDataSource.getStatisticType()).isEqualTo(entity.getStatisticType());
        assertThat(importedDataSource.getDescription()).isEqualTo(entity.getDescription());

        assertThat(importedDataSource.getConfig()).isEqualTo(entity.getConfig());
    }
}
