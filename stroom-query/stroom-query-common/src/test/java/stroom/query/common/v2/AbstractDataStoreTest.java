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

package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.Format;
import stroom.query.api.OffsetRange;
import stroom.query.api.ParamUtil;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.Row;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.Sort;
import stroom.query.api.Sort.SortDirection;
import stroom.query.api.TableResult;
import stroom.query.api.TableSettings;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValString;
import stroom.util.logging.SimpleMetrics;
import stroom.util.shared.ModelStringUtil;

import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractDataStoreTest {

    @BeforeAll
    static void beforeAll() {
        SimpleMetrics.setEnabled(true);
    }

    void basicTest() {
        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(Column.builder()
                        .id("Text")
                        .name("Text")
                        .expression(ParamUtil.create("Text"))
                        .format(Format.TEXT)
                        .build())
                .build();

        final DataStore dataStore = create(tableSettings);

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + i;
            dataStore.accept(Val.of(ValString.create(text)));
        }

        // Wait for all items to be added.
        try {
            dataStore.getCompletionState().signalComplete();
            dataStore.getCompletionState().awaitCompletion();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        // Make sure we only get 50 results.
        final ResultRequest tableResultRequest = ResultRequest.builder()
                .componentId("componentX")
                .addMappings(tableSettings)
                .requestedRange(new OffsetRange(0, 50))
                .build();
        final TableResultCreator tableComponentResultCreator = new TableResultCreator();
        final TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                dataStore,
                tableResultRequest);
        assertThat(searchResult.getResultRange().getLength()).isEqualTo(50);
    }

    void nestedTest() {
        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(Column.builder()
                        .id("Col1")
                        .name("Col1")
                        .expression(ParamUtil.create("Col1"))
                        .format(Format.NUMBER)
                        .group(0)
                        .sort(Sort.builder().order(0).build())
                        .build())
                .addColumns(Column.builder()
                        .id("Col2")
                        .name("Col2")
                        .expression(ParamUtil.create("Col2"))
                        .format(Format.NUMBER)
                        .group(1)
                        .sort(Sort.builder().order(1).build())
                        .build())
                .addColumns(Column.builder()
                        .id("Col3")
                        .name("Col3")
                        .expression(ParamUtil.create("Col3"))
                        .format(Format.NUMBER)
                        .group(2)
                        .sort(Sort.builder().order(2).build())
                        .build())
                .build();

        final DataStore dataStore = createUnlimitedDataStore(tableSettings);

        for (long i = 1; i <= 10; i++) {
            for (long j = 1; j <= 10; j++) {
                for (long k = 1; k <= 10; k++) {
                    dataStore.accept(Val.of(
                            ValLong.create(i),
                            ValLong.create(j),
                            ValLong.create(k)));
                }
            }
        }

        // Wait for all items to be added.
        try {
            dataStore.getCompletionState().signalComplete();
            dataStore.getCompletionState().awaitCompletion();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        final TableResultCreator tableComponentResultCreator = new TableResultCreator();

        // Make sure we only get 10 results.
        ResultRequest tableResultRequest = ResultRequest.builder()
                .componentId("componentX")
                .addMappings(tableSettings)
                .requestedRange(new OffsetRange(0, 10000))
                .build();
        TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                dataStore,
                tableResultRequest);

        testRows(searchResult, 1);

        // Now open all first level groups.
        Set<String> openGroups = searchResult
                .getRows()
                .stream()
                .map(Row::getGroupKey)
                .collect(Collectors.toSet());
        tableResultRequest = ResultRequest.builder()
                .componentId("componentX")
                .addMappings(tableSettings)
                .requestedRange(new OffsetRange(0, 10000))
                .openGroups(openGroups)
                .build();
        searchResult = (TableResult) tableComponentResultCreator.create(
                dataStore,
                tableResultRequest);

        testRows(searchResult, 2);

        // Now open all first and second level groups.
        openGroups = searchResult
                .getRows()
                .stream()
                .map(Row::getGroupKey)
                .collect(Collectors.toSet());
        tableResultRequest = ResultRequest.builder()
                .componentId("componentX")
                .addMappings(tableSettings)
                .requestedRange(new OffsetRange(0, 10000))
                .openGroups(openGroups)
                .build();
        searchResult = (TableResult) tableComponentResultCreator.create(
                dataStore,
                tableResultRequest);

        testRows(searchResult, 3);
    }

    private void testRows(final TableResult searchResult, final int maxDepth) {
        // Create expected test rows.
        final List<List<String>> expectedRows = new ArrayList<>();
        createRows(expectedRows, Collections.emptyList(), 1, maxDepth, 10, 3);

        // Test row count.
        assertThat(searchResult.getResultRange().getLength()).isEqualTo(expectedRows.size());

        // Test rows.
        int i = 0;
        for (final Row row : searchResult.getRows()) {
            final List<String> rowValues = row.getValues();
            assertThat(rowValues).isEqualTo(expectedRows.get(i++));
        }
    }

    private void createRows(final List<List<String>> rows,
                            final List<String> parentRow,
                            final int currentDepth,
                            final int maxDepth,
                            final int count,
                            final int columns) {
        for (long i = 1; i <= count; i++) {
            final List<String> newParentRow = new ArrayList<>(parentRow);
            newParentRow.add(Long.toString(i));

            final List<String> row = new ArrayList<>(newParentRow);
            for (int j = row.size(); j < columns; j++) {
                row.add(null);
            }
            rows.add(row);
            if (currentDepth < maxDepth) {
                createRows(rows, newParentRow, currentDepth + 1, maxDepth, count, columns);
            }
        }
    }

    void noValuesTest() {
        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(Column.builder()
                        .id("currentUser")
                        .name("currentUser")
                        .expression("currentUser()")
                        .format(Format.TEXT)
                        .build())
                .build();

        final DataStore dataStore = create(tableSettings);

        for (int i = 0; i < 1; i++) {
            dataStore.accept(Val.of(ValString.create("jbloggs")));
        }

        // Wait for all items to be added.
        try {
            dataStore.getCompletionState().signalComplete();
            dataStore.getCompletionState().awaitCompletion();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        // Make sure we only get 50 results.
        final ResultRequest tableResultRequest = ResultRequest.builder()
                .componentId("componentX")
                .addMappings(tableSettings)
                .requestedRange(new OffsetRange(0, 1))
                .build();
        final TableResultCreator tableComponentResultCreator = new TableResultCreator();
        final TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                dataStore,
                tableResultRequest);
        assertThat(searchResult.getTotalResults().intValue()).isEqualTo(1);
    }

    void testBigBigResult() {
        for (int i = 0; i < 20; i++) {
            System.out.println("\n------ RUN " + (i + 1) + " -------");
            final long start = System.currentTimeMillis();
            testBigResult();
            System.out.println("Took " + ModelStringUtil.formatDurationString(System.currentTimeMillis() - start));
        }
    }

    void testBigResult() {
        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(Column.builder()
                        .id("Text")
                        .name("Text")
                        .expression(ParamUtil.create("Text"))
                        .format(Format.TEXT)
                        .group(0)
                        .build())
                .addColumns(Column.builder()
                        .id("Text2")
                        .name("Text2")
                        .expression(ParamUtil.create("Text2"))
                        .format(Format.TEXT)
                        .build())
                .showDetail(true)
                .build();

        final DataStore dataStore = createUnlimitedDataStore(tableSettings);

        SimpleMetrics.measure("Loaded data", () -> {
            for (int i = 0; i < 100; i++) {
                final String key = UUID.randomUUID().toString();
                for (int j = 0; j < 100000; j++) {
                    final String value = UUID.randomUUID().toString();
                    dataStore.accept(Val.of(ValString.create(key), ValString.create(value)));
                }
            }
        });

        // Wait for all items to be added.
        try {
            dataStore.getCompletionState().signalComplete();
            dataStore.getCompletionState().awaitCompletion();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        System.out.println("\nLoading data");
        SimpleMetrics.report();

        System.out.println("\nGetting data");
        SimpleMetrics.report();

        //Getting the runtime reference from system
        final Runtime runtime = Runtime.getRuntime();

        runtime.gc();

        //Print used memory
        System.out.println("Used Memory: "
                           + ModelStringUtil.formatIECByteSizeString(runtime.totalMemory() - runtime.freeMemory()));

        SimpleMetrics.measure("Result", () -> {
            // Make sure we only get 50 results.
            final ResultRequest tableResultRequest = ResultRequest.builder()
                    .componentId("componentX")
                    .addMappings(tableSettings)
                    .requestedRange(new OffsetRange(0, 50))
                    .build();
            final TableResultCreator tableComponentResultCreator = new TableResultCreator();
            final TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                    dataStore,
                    tableResultRequest);

            assertThat(searchResult.getTotalResults().intValue()).isEqualTo(50);
        });

        System.out.println("\nGetting results");
        SimpleMetrics.report();

    }

    void sortedTextTest() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(Column.builder()
                        .id("Text")
                        .name("Text")
                        .expression(ParamUtil.create("Text"))
                        .sort(sort)
                        .build())
                .build();

        final DataStore dataStore = create(tableSettings);

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            dataStore.accept(Val.of(ValString.create(text)));
        }

        // Wait for all items to be added.
        try {
            dataStore.getCompletionState().signalComplete();
            dataStore.getCompletionState().awaitCompletion();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        final ResultRequest tableResultRequest = ResultRequest.builder()
                .componentId("componentX")
                .addMappings(tableSettings)
                .requestedRange(new OffsetRange(0, 50))
                .build();
        checkResults(dataStore, tableResultRequest, 0, false);
    }

    void sortedNumberTest() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(Column.builder()
                        .id("Number")
                        .name("Number")
                        .expression(ParamUtil.create("Number"))
                        .sort(sort)
                        .build())
                .build();

        final DataStore dataStore = create(tableSettings);

        for (int i = 0; i < 3000; i++) {
            final String text = String.valueOf((int) (Math.random() * 100));
            dataStore.accept(Val.of(ValString.create(text)));
        }

        // Wait for all items to be added.
        try {
            dataStore.getCompletionState().signalComplete();
            dataStore.getCompletionState().awaitCompletion();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        final ResultRequest tableResultRequest =
                ResultRequest.builder()
                        .componentId("componentX")
                        .addMappings(tableSettings)
                        .requestedRange(new OffsetRange(0, 50))
                        .build();
        checkResults(dataStore, tableResultRequest, 0, true);
    }

    void sortedCountedTextTest1() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(Column.builder()
                        .id("Count")
                        .name("Count")
                        .expression("count()")
                        .sort(sort)
                        .build())
                .addColumns(Column.builder()
                        .id("Text")
                        .name("Text")
                        .expression(ParamUtil.create("Text"))
                        .group(0)
                        .build())
                .build();

        final DataStore dataStore = create(tableSettings);

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            dataStore.accept(Val.of(ValString.create(text)));
        }

        // Wait for all items to be added.
        try {
            dataStore.getCompletionState().signalComplete();
            dataStore.getCompletionState().awaitCompletion();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        final ResultRequest tableResultRequest =
                ResultRequest.builder()
                        .componentId("componentX")
                        .addMappings(tableSettings)
                        .requestedRange(new OffsetRange(0, 50))
                        .build();
        checkResults(dataStore, tableResultRequest, 0, true);
    }

    void sortedCountedTextTest2() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(Column.builder()
                        .id("Count")
                        .name("Count")
                        .expression("count()")
                        .build())
                .addColumns(Column.builder()
                        .id("Text")
                        .name("Text")
                        .expression(ParamUtil.create("Text"))
                        .sort(sort)
                        .group(0)
                        .build())
                .build();

        final DataStore dataStore = create(tableSettings);

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            dataStore.accept(Val.of(ValString.create(text)));
        }

        // Wait for all items to be added.
        try {
            dataStore.getCompletionState().signalComplete();
            dataStore.getCompletionState().awaitCompletion();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        final ResultRequest tableResultRequest =
                ResultRequest.builder()
                        .componentId("componentX")
                        .addMappings(tableSettings)
                        .requestedRange(new OffsetRange(0, 50))
                        .build();
        checkResults(dataStore, tableResultRequest, 1, false);
    }

    void sortedCountedTextTest3() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(Column.builder()
                        .id("Count")
                        .name("Count")
                        .expression("count()")
                        .build())
                .addColumns(Column.builder()
                        .id("Text")
                        .name("Text")
                        .expression(ParamUtil.create("Text"))
                        .sort(sort)
                        .group(0)
                        .build())
                .build();

        final DataStore dataStore = create(tableSettings);

        for (int i = 0; i < 3000; i++) {
            final String text = "Text " + (int) (Math.random() * 100);
            dataStore.accept(Val.of(ValString.create(text)));
        }

        // Wait for all items to be added.
        try {
            dataStore.getCompletionState().signalComplete();
            dataStore.getCompletionState().awaitCompletion();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        final ResultRequest tableResultRequest =
                ResultRequest.builder()
                        .componentId("componentX")
                        .addMappings(tableSettings)
                        .requestedRange(new OffsetRange(0, 50))
                        .build();
        checkResults(dataStore, tableResultRequest, 1, false);
    }

    void firstLastSelectorTest() {
        final Sort sort = new Sort(0, SortDirection.ASCENDING);

        final String param = ParamUtil.create("Number");
        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(Column.builder()
                        .id("Group")
                        .name("Group")
                        .expression("${group}")
                        .group(0)
                        .build())
                .addColumns(Column.builder()
                        .id("Number")
                        .name("Number")
                        .expression("concat(first(" + param + "), ' ', last(" + param + "))")
                        .build())
                .addColumns(Column.builder()
                        .id("Number Sorted")
                        .name("Number Sorted")
                        .expression(param)
                        .sort(sort)
                        .build())
                .build();

        final DataStore dataStore = create(tableSettings);

        for (int i = 1; i <= 30; i++) {
            dataStore.accept(Val.of(ValString.create("group"), ValLong.create(i)));
        }

        // Wait for all items to be added.
        try {
            dataStore.getCompletionState().signalComplete();
            dataStore.getCompletionState().awaitCompletion();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        final ResultRequest tableResultRequest =
                ResultRequest.builder()
                        .componentId("componentX")
                        .addMappings(tableSettings)
                        .requestedRange(new OffsetRange(0, 50))
                        .build();

        final TableResultCreator tableComponentResultCreator = new TableResultCreator();
        final TableResult searchResult = (TableResult) tableComponentResultCreator.create(dataStore,
                tableResultRequest);

        assertThat(searchResult.getTotalResults()).isOne();

        for (final Row result : searchResult.getRows()) {
            final String value = result.getValues().get(1);
            System.out.println(value);
            assertThat(value).isEqualTo("1 30");
        }
    }

    private void checkResults(final DataStore dataStore,
                              final ResultRequest tableResultRequest,
                              final int sortCol,
                              final boolean numeric) {
        // Make sure we only get 2000 results.
        final TableResultCreator tableComponentResultCreator = new TableResultCreator();
        final TableResult searchResult = (TableResult) tableComponentResultCreator.create(dataStore,
                tableResultRequest);

        assertThat(searchResult.getResultRange().getLength() <= 50).isTrue();
        assertThat(searchResult.getTotalResults() <= 3000).isTrue();

        String lastValue = null;
        for (final Row result : searchResult.getRows()) {
            final String value = result.getValues().get(sortCol);
            if (lastValue != null) {
                if (numeric) {
                    final double d1 = Double.parseDouble(lastValue);
                    final double d2 = Double.parseDouble(value);

                    final int diff = Double.compare(d1, d2);

                    assertThat(diff <= 0).isTrue();
                } else {
                    final int diff = lastValue.compareTo(value);
                    assertThat(diff <= 0).isTrue();
                }
            }
            lastValue = value;
        }
    }

    DataStore create(final TableSettings tableSettings) {
        // Create a set of sizes that are the minimum values for the combination of user provided sizes for the table
        // and the default maximum sizes.
        final Sizes defaultMaxResultsSizes = Sizes.create(50);
        final Sizes maxResults = Sizes.min(Sizes.create(tableSettings.getMaxResults()), defaultMaxResultsSizes);
        final DataStoreSettings dataStoreSettings = DataStoreSettings
                .createBasicSearchResultStoreSettings()
                .copy()
                .maxResults(maxResults)
                .build();
        return create(tableSettings, dataStoreSettings);
    }

    DataStore createUnlimitedDataStore(final TableSettings tableSettings) {
        final DataStoreSettings dataStoreSettings = DataStoreSettings
                .createBasicSearchResultStoreSettings()
                .copy()
                .maxResults(Sizes.unlimited())
                .build();
        return create(tableSettings, dataStoreSettings);
    }

    DataStore create(final TableSettings tableSettings, final DataStoreSettings dataStoreSettings) {
        return create(
                SearchRequestSource.createBasic(),
                new QueryKey(UUID.randomUUID().toString()),
                "0",
                tableSettings,
                new SearchResultStoreConfig(),
                dataStoreSettings,
                UUID.randomUUID().toString());
    }

    abstract DataStore create(SearchRequestSource searchRequestSource,
                              QueryKey queryKey,
                              String componentId,
                              TableSettings tableSettings,
                              SearchResultStoreConfig resultStoreConfig,
                              DataStoreSettings dataStoreSettings,
                              String subDirectory);
}
