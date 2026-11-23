/*
 * Copyright 2020 Crown Copyright
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

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.lmdb.LmdbLibrary;
import stroom.lmdb.LmdbLibraryConfig;
import stroom.lmdb2.LmdbEnv;
import stroom.lmdb2.LmdbEnvDir;
import stroom.lmdb2.LmdbEnvDirFactory;
import stroom.query.api.Column;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Format;
import stroom.query.api.HoppingWindow;
import stroom.query.api.OffsetRange;
import stroom.query.api.ParamUtil;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.Row;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.TableResult;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.SimpleMetrics;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestLmdbDataStore extends AbstractDataStoreTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLmdbDataStore.class);

    private ErrorConsumerImpl lastErrorConsumer;

    private Path tempDir;
    private ExecutorService executorService;

    @BeforeEach
    void setup(@TempDir final Path tempDir) {
        this.tempDir = tempDir;
        executorService = Executors.newCachedThreadPool();
    }

    @AfterEach
    void after() {
        // Clear the stores before closing the executor. JUnit runs subclass @AfterEach methods
        // before superclass ones, so without this explicit call the executor close would run
        // first and, for a test that failed leaving a store unterminated, await that store's
        // still-polling transfer task forever - turning a test failure into a hang. Clearing
        // terminates every store's transfer thread, so the close() below then completes, giving
        // deterministic ordering: transfer threads finished before JUnit deletes the @TempDir.
        clearCreatedStores();
        executorService.close();
    }

    @Override
    DataStore create(final SearchRequestSource searchRequestSource,
                     final QueryKey queryKey,
                     final String componentId,
                     final TableSettings tableSettings,
                     final SearchResultStoreConfig resultStoreConfig,
                     final DataStoreSettings dataStoreSettings,
                     final String subDirectory) {
        final FieldIndex fieldIndex = new FieldIndex();

        final TempDirProvider tempDirProvider = () -> tempDir;
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final LmdbLibraryConfig lmdbLibraryConfig = new LmdbLibraryConfig();
        final LmdbEnvDirFactory lmdbEnvDirFactory = new LmdbEnvDirFactory(
                new LmdbLibrary(pathCreator, tempDirProvider, () -> lmdbLibraryConfig), pathCreator);
        final LmdbEnvDir lmdbEnvDir = lmdbEnvDirFactory
                .builder()
                .config(resultStoreConfig.getLmdbConfig())
                .subDir(subDirectory)
                .build();
        final LmdbEnv.Builder lmdbEnvBuilder = LmdbEnv
                .builder()
                .config(resultStoreConfig.getLmdbConfig())
                .lmdbEnvDir(lmdbEnvDir);
        final ErrorConsumerImpl errorConsumer = new ErrorConsumerImpl();
        lastErrorConsumer = errorConsumer;
        return new LmdbDataStore(
                searchRequestSource,
                lmdbEnvBuilder,
                resultStoreConfig,
                queryKey,
                componentId,
                tableSettings,
                new ExpressionContext(),
                fieldIndex,
                Collections.emptyMap(),
                dataStoreSettings,
                () -> executorService,
                errorConsumer,
                new ByteBufferFactoryImpl(),
                new ExpressionPredicateFactory(),
                AnnotationMapperFactory.NO_OP,
                //TODO: DS
                null);
    }

    @Test
    void windowGroupsRowsByTheWindowTheyAreIn() {
        final TableSettings tableSettings = windowTable("1d", null);
        final DataStore dataStore = create(tableSettings);

        addEvent(dataStore, "2026-03-10T09:14:00Z", "user1");
        addEvent(dataStore, "2026-03-10T23:59:59.999Z", "user1");
        addEvent(dataStore, "2026-03-11T00:00:00Z", "user1");
        addEvent(dataStore, "2026-03-10T12:00:00Z", "user2");

        assertThat(windowRows(dataStore, tableSettings)).containsExactlyInAnyOrder(
                List.of("user1", "2026-03-10T00:00:00.000Z", "2026-03-11T00:00:00.000Z", "2"),
                List.of("user1", "2026-03-11T00:00:00.000Z", "2026-03-12T00:00:00.000Z", "1"),
                List.of("user2", "2026-03-10T00:00:00.000Z", "2026-03-11T00:00:00.000Z", "1"));
    }

    @Test
    void hoppingWindowCountsARowInEveryWindowThatCoversIt() {
        final TableSettings tableSettings = windowTable("1h", "30m");
        final DataStore dataStore = create(tableSettings);

        addEvent(dataStore, "2026-03-10T10:10:00Z", "user1");
        addEvent(dataStore, "2026-03-10T10:40:00Z", "user1");
        addEvent(dataStore, "2026-03-10T11:20:00Z", "user1");

        // The hour from 10:30 holds the events at 10:40 and 11:20, which the old window code never
        // counted together.
        assertThat(windowRows(dataStore, tableSettings)).containsExactlyInAnyOrder(
                List.of("user1", "2026-03-10T09:30:00.000Z", "2026-03-10T10:30:00.000Z", "1"),
                List.of("user1", "2026-03-10T10:00:00.000Z", "2026-03-10T11:00:00.000Z", "2"),
                List.of("user1", "2026-03-10T10:30:00.000Z", "2026-03-10T11:30:00.000Z", "2"),
                List.of("user1", "2026-03-10T11:00:00.000Z", "2026-03-10T12:00:00.000Z", "1"));
    }

    @Test
    void rowWithNoWindowTimeIsLeftOutAndReportedOnce() {
        final TableSettings tableSettings = windowTable("1d", null);
        final DataStore dataStore = create(tableSettings);

        addEvent(dataStore, "2026-03-10T09:14:00Z", "user1");
        dataStore.accept(Val.of(ValNull.INSTANCE, ValString.create("user1")));
        dataStore.accept(Val.of(ValString.create("not a time"), ValString.create("user1")));
        // A date written as text is still a time.
        dataStore.accept(Val.of(ValString.create("2026-03-10T10:00:00.000Z"), ValString.create("user1")));

        assertThat(windowRows(dataStore, tableSettings)).containsExactly(
                List.of("user1", "2026-03-10T00:00:00.000Z", "2026-03-11T00:00:00.000Z", "2"));
        assertThat(lastErrorConsumer.getErrorMessages())
                .hasSize(1)
                .first()
                .satisfies(message -> assertThat(message.getMessage())
                        .isEqualTo("2 rows with no time in the window field 'EventTime' were left out of "
                                   + "the results"));
    }

    @Test
    void lagAndLeadGiveTheCountOfTheWindowsEitherSide() {
        final TableSettings tableSettings = lagTable("1d", null);
        final DataStore dataStore = create(tableSettings);

        addEvent(dataStore, "2026-03-10T09:00:00Z", "user1");
        addEvent(dataStore, "2026-03-10T10:00:00Z", "user1");
        addEvent(dataStore, "2026-03-11T09:00:00Z", "user1");
        addEvent(dataStore, "2026-03-13T09:00:00Z", "user1");
        // Another user's events are not counted in the windows of the first.
        addEvent(dataStore, "2026-03-11T09:00:00Z", "user2");
        addEvent(dataStore, "2026-03-12T09:00:00Z", "user2");

        // The columns are the user, the start of the window, its count, the count of the window
        // before and the count of the window after. A user has a row for a day they did nothing on
        // if a day either side reads it, as user1 does for the 12th. The data runs from the 10th to
        // the 13th, so there are no rows for the 9th or the 14th, and the days outside that range
        // have no count, where an empty day inside it has a count of zero.
        assertThat(windowRows(dataStore, tableSettings, 5)).containsExactlyInAnyOrder(
                Arrays.asList("user1", "2026-03-10T00:00:00.000Z", "2", null, "1"),
                Arrays.asList("user1", "2026-03-11T00:00:00.000Z", "1", "2", "0"),
                Arrays.asList("user1", "2026-03-12T00:00:00.000Z", "0", "1", "1"),
                Arrays.asList("user1", "2026-03-13T00:00:00.000Z", "1", "0", null),
                Arrays.asList("user2", "2026-03-10T00:00:00.000Z", "0", null, "1"),
                Arrays.asList("user2", "2026-03-11T00:00:00.000Z", "1", "0", "1"),
                Arrays.asList("user2", "2026-03-12T00:00:00.000Z", "1", "1", "0"),
                Arrays.asList("user2", "2026-03-13T00:00:00.000Z", "0", "1", null));
    }

    // A search across nodes builds a store on each node and sends its rows to the node that the
    // search was started on, which merges them. The result is the same as if one store had been
    // given every event.
    @Test
    void lagAndLeadOfRowsMergedFromTwoNodes() throws InterruptedException {
        final TableSettings tableSettings = lagTable("1d", null);
        final DataStoreSettings nodeSettings = DataStoreSettings.createPayloadProducerSearchResultStoreSettings();
        final DataStore node1 = create(tableSettings, nodeSettings);
        final DataStore node2 = create(tableSettings, nodeSettings);
        final DataStore dataStore = create(tableSettings);

        // The events of the same user and day are split between the nodes.
        addEvent(node1, "2026-03-10T09:00:00Z", "user1");
        addEvent(node2, "2026-03-10T10:00:00Z", "user1");
        addEvent(node2, "2026-03-11T09:00:00Z", "user1");
        addEvent(node1, "2026-03-13T09:00:00Z", "user1");
        addEvent(node1, "2026-03-11T09:00:00Z", "user2");
        addEvent(node2, "2026-03-12T09:00:00Z", "user2");

        transferPayloads(node1, dataStore);
        transferPayloads(node2, dataStore);

        // The same rows as lagAndLeadGiveTheCountOfTheWindowsEitherSide().
        assertThat(windowRows(dataStore, tableSettings, 5)).containsExactlyInAnyOrder(
                Arrays.asList("user1", "2026-03-10T00:00:00.000Z", "2", null, "1"),
                Arrays.asList("user1", "2026-03-11T00:00:00.000Z", "1", "2", "0"),
                Arrays.asList("user1", "2026-03-12T00:00:00.000Z", "0", "1", "1"),
                Arrays.asList("user1", "2026-03-13T00:00:00.000Z", "1", "0", null),
                Arrays.asList("user2", "2026-03-10T00:00:00.000Z", "0", null, "1"),
                Arrays.asList("user2", "2026-03-11T00:00:00.000Z", "1", "0", "1"),
                Arrays.asList("user2", "2026-03-12T00:00:00.000Z", "1", "1", "0"),
                Arrays.asList("user2", "2026-03-13T00:00:00.000Z", "0", "1", null));
    }

    // A node sends the windows it holds real rows for with its rows, so the store they are merged into
    // does not read through all of its rows to find its windows.
    @Test
    void rowsMergedFromTwoNodesDoNotMakeTheStoreReadThroughItsRows() throws InterruptedException {
        final TableSettings tableSettings = lagTable("1d", null);
        final DataStoreSettings nodeSettings = DataStoreSettings.createPayloadProducerSearchResultStoreSettings();
        final DataStore node1 = create(tableSettings, nodeSettings);
        final DataStore node2 = create(tableSettings, nodeSettings);
        final DataStore dataStore = create(tableSettings);

        // Only node2 has a real row on the 13th, so the merged store must learn of it from node2.
        addEvent(node1, "2026-03-10T09:00:00Z", "user1");
        addEvent(node2, "2026-03-10T10:00:00Z", "user1");
        addEvent(node2, "2026-03-13T09:00:00Z", "user1");

        transferPayloads(node1, dataStore);
        transferPayloads(node2, dataStore);

        assertThat(windowRows(dataStore, tableSettings, 5)).containsExactlyInAnyOrder(
                Arrays.asList("user1", "2026-03-10T00:00:00.000Z", "2", null, "0"),
                Arrays.asList("user1", "2026-03-11T00:00:00.000Z", "0", "2", "0"),
                Arrays.asList("user1", "2026-03-12T00:00:00.000Z", "0", "0", "1"),
                Arrays.asList("user1", "2026-03-13T00:00:00.000Z", "1", "0", null));
        assertThat(((LmdbDataStore) dataStore).getWindowExtentScanCount()).isZero();
    }

    @Test
    void hoppingWindowRowsMergedFromTwoNodes() throws InterruptedException {
        final TableSettings tableSettings = windowTable("1h", "30m");
        final DataStoreSettings nodeSettings = DataStoreSettings.createPayloadProducerSearchResultStoreSettings();
        final DataStore node1 = create(tableSettings, nodeSettings);
        final DataStore node2 = create(tableSettings, nodeSettings);
        final DataStore single = create(tableSettings);
        final DataStore merged = create(tableSettings);

        final List<String> times = List.of(
                "2026-03-10T10:10:00Z",
                "2026-03-10T10:40:00Z",
                "2026-03-10T10:50:00Z",
                "2026-03-10T11:20:00Z");
        for (int i = 0; i < times.size(); i++) {
            addEvent(single, times.get(i), "user1");
            addEvent(i % 2 == 0
                    ? node1
                    : node2, times.get(i), "user1");
        }
        transferPayloads(node1, merged);
        transferPayloads(node2, merged);

        assertThat(windowRows(merged, tableSettings))
                .isNotEmpty()
                .containsExactlyInAnyOrderElementsOf(windowRows(single, tableSettings));
    }

    @Test
    void lagOfAHoppingWindowReadsTheWindowOneSizeBefore() {
        final TableSettings tableSettings = lagTable("1h", "30m");
        final DataStore dataStore = create(tableSettings);

        addEvent(dataStore, "2026-03-10T10:10:00Z", "user1");
        addEvent(dataStore, "2026-03-10T10:40:00Z", "user1");
        addEvent(dataStore, "2026-03-10T11:20:00Z", "user1");

        // The hour from 11:00 holds one event, and the hour before it, from 10:00, holds two. The
        // window that starts half an hour before is not the one that is read.
        assertThat(windowRows(dataStore, tableSettings, 5)).containsExactlyInAnyOrder(
                Arrays.asList("user1", "2026-03-10T09:30:00.000Z", "1", null, "2"),
                Arrays.asList("user1", "2026-03-10T10:00:00.000Z", "2", null, "1"),
                Arrays.asList("user1", "2026-03-10T10:30:00.000Z", "2", "1", null),
                Arrays.asList("user1", "2026-03-10T11:00:00.000Z", "1", "2", null));
    }

    @Test
    void rowsCopiedFromAnotherWindowAreNotAmongTheRowsOfTheWindow() {
        // Showing detail keeps every row below its group.
        final TableSettings tableSettings = lagTable("1d", null).copy().showDetail(true).build();
        final DataStore dataStore = create(tableSettings);

        addEvent(dataStore, "2026-03-10T09:00:00Z", "user1");
        addEvent(dataStore, "2026-03-10T10:00:00Z", "user1");
        addEvent(dataStore, "2026-03-11T09:00:00Z", "user1");

        // Three events give three rows below the groups, and not one more for each copy.
        final long detailRows = windowRowDepths(dataStore, tableSettings)
                .stream()
                .filter(depth -> depth > 0)
                .count();
        assertThat(detailRows).isEqualTo(3);
    }

    @Test
    void comparisonOfTwoExpressionsFiltersRows() {
        // What `having count() > lag(count(), 1)` is parsed into: a hidden column that is true or
        // false, and a filter on it.
        final TableSettings tableSettings = lagTable("1d", null)
                .copy()
                .addColumns(Column.builder()
                        .id("__having_1__")
                        .name("having_1")
                        .expression("count() > lag(count(), 1, 'window:1d|||Z')")
                        .visible(false)
                        .special(true)
                        .build())
                .aggregateFilter(ExpressionOperator.builder()
                        .addTerm(ExpressionTerm.builder()
                                .field("having_1")
                                .condition(Condition.EQUALS)
                                .value("true")
                                .build())
                        .build())
                .build();
        final DataStore dataStore = create(tableSettings);

        addEvent(dataStore, "2026-03-10T09:00:00Z", "user1");
        addEvent(dataStore, "2026-03-11T09:00:00Z", "user1");
        addEvent(dataStore, "2026-03-11T10:00:00Z", "user1");
        addEvent(dataStore, "2026-03-12T09:00:00Z", "user1");

        // Only the 11th has more events than the day before it. The 10th has no day before to compare
        // with, and the 12th has fewer.
        assertThat(windowRows(dataStore, tableSettings, 5)).containsExactly(
                Arrays.asList("user1", "2026-03-11T00:00:00.000Z", "2", "1", "1"));
    }

    @Test
    void closedWindowTimeIsTheStartOfTheLatestWindowToHaveEnded() {
        final LmdbDataStore dataStore = (LmdbDataStore) create(windowTable("1h", null));

        assertThat(Instant.ofEpochMilli(dataStore.getClosedWindowTime(
                Instant.parse("2026-03-10T10:25:00Z").toEpochMilli())))
                .isEqualTo(Instant.parse("2026-03-10T09:00:00Z"));
    }

    @Test
    void closedWindowTimeWaitsForTheWindowThatLeadReads() {
        // The table reads the window after a row's own, so a row is closed when that one has ended.
        final LmdbDataStore dataStore = (LmdbDataStore) create(lagTable("1h", null));

        assertThat(Instant.ofEpochMilli(dataStore.getClosedWindowTime(
                Instant.parse("2026-03-10T10:25:00Z").toEpochMilli())))
                .isEqualTo(Instant.parse("2026-03-10T08:00:00Z"));
    }

    @Test
    void closedWindowTimeOfATableWithNoWindowsIsTheTimeGiven() {
        final LmdbDataStore dataStore = (LmdbDataStore) create(TableSettings.builder()
                .addColumns(Column.builder()
                        .id("UserId")
                        .name("UserId")
                        .expression("${UserId}")
                        .build())
                .build());

        assertThat(dataStore.getClosedWindowTime(1_234L)).isEqualTo(1_234L);
    }

    @Test
    void windowIsKeptUntilItIsJudgedAndNoLaterWindowReadsIt() {
        // The table reads the window before a row's own. The 10th has been judged, and the 11th,
        // which is next, reads the 10th, so every day before the 9th may be deleted.
        final LmdbDataStore dataStore = (LmdbDataStore) create(lagTable("1d", null));

        assertThat(Instant.ofEpochMilli(dataStore.getDeletableWindowTime(
                Instant.parse("2026-03-10T00:00:00Z").toEpochMilli())))
                .isEqualTo(Instant.parse("2026-03-08T23:59:59.999Z"));
        // Nothing has been judged, so nothing may be deleted.
        assertThat(dataStore.getDeletableWindowTime(null)).isNegative();
    }

    @Test
    void tableWithNoWindowsDoesNotLimitWhatIsDeleted() {
        final LmdbDataStore dataStore = (LmdbDataStore) create(TableSettings.builder()
                .addColumns(Column.builder()
                        .id("UserId")
                        .name("UserId")
                        .expression("${UserId}")
                        .build())
                .build());

        assertThat(dataStore.getDeletableWindowTime(null)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void tooManyCopiesOfEachRowIsRefused() {
        // Each row is in 60 windows, and is copied again to each for lag and for lead.
        assertThatThrownBy(() -> create(lagTable("1h", "1m")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Each event would be copied 180 times")
                .hasMessageContaining("the most allowed is 100");
    }

    @Test
    void windowFunctionInATableWithNoWindowIsRefused() {
        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(Column.builder()
                        .id("Starts")
                        .name("Starts")
                        .expression("windowStart('window:1h|||Z')")
                        .build())
                .build();

        assertThatThrownBy(() -> create(tableSettings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("can only be used in a query that has a window clause");
    }

    // A table need not come from StroomQL, which is where the limits on a window are first applied.
    @Test
    void windowThatStroomQlWouldRefuseIsRefusedByTheStore() {
        assertThatThrownBy(() -> create(windowTable("1d", "1m")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Each event would be in 1440 windows, and the most allowed is 100");

        final TableSettings notGrouped = TableSettings.builder()
                .window(new HoppingWindow("EventTime", "1h", "30m", null, "Z"))
                .addColumns(Column.builder().id("Events").name("Events").expression("count()").build())
                .build();
        assertThatThrownBy(() -> create(notGrouped))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must have a column that is grouped by windowKey()");

        final TableSettings noTimeField = windowTable("1d", null)
                .copy()
                .window(new HoppingWindow(null, "1d", null, null, "Z"))
                .build();
        assertThatThrownBy(() -> create(noTimeField))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no time field");
    }

    // A table of events per user per window, as `window EventTime by <size> advance <advance>` with
    // `group by UserId` and `select UserId, windowStart(), windowEnd(), count()` is parsed into.
    private TableSettings windowTable(final String size, final String advance) {
        final HoppingWindow window = new HoppingWindow("EventTime", size, advance, null, "Z");
        final String windows = "'window:" + size + "|" + (advance == null
                ? ""
                : advance) + "||Z'";
        return TableSettings.builder()
                .window(window)
                .addColumns(Column.builder()
                        .id("UserId")
                        .name("UserId")
                        .expression(ParamUtil.create("UserId"))
                        .group(0)
                        .build())
                .addColumns(Column.builder()
                        .id("Starts")
                        .name("Starts")
                        .expression("windowStart(" + windows + ")")
                        .build())
                .addColumns(Column.builder()
                        .id("Ends")
                        .name("Ends")
                        .expression("windowEnd(" + windows + ")")
                        .build())
                .addColumns(Column.builder()
                        .id("Events")
                        .name("Events")
                        .expression("count()")
                        .build())
                .addColumns(Column.builder()
                        .id("__window__")
                        .name("__window__")
                        .expression("windowKey(" + windows + ")")
                        .group(0)
                        .visible(false)
                        .special(true)
                        .build())
                .build();
    }

    // A table of the events of each user in each window, with the events of the windows before and
    // after, as `select UserId, windowStart(), count(), lag(count(), 1), lead(count(), 1)` is parsed
    // into.
    private TableSettings lagTable(final String size, final String advance) {
        final HoppingWindow window = new HoppingWindow("EventTime", size, advance, null, "Z");
        final String windows = "'window:" + size + "|" + (advance == null
                ? ""
                : advance) + "||Z'";
        return TableSettings.builder()
                .window(window)
                .addColumns(Column.builder()
                        .id("UserId")
                        .name("UserId")
                        .expression(ParamUtil.create("UserId"))
                        .group(0)
                        .build())
                .addColumns(Column.builder()
                        .id("Starts")
                        .name("Starts")
                        .expression("windowStart(" + windows + ")")
                        .build())
                .addColumns(Column.builder()
                        .id("Events")
                        .name("Events")
                        .expression("count()")
                        .build())
                .addColumns(Column.builder()
                        .id("Before")
                        .name("Before")
                        .expression("lag(count(), 1, " + windows + ")")
                        .build())
                .addColumns(Column.builder()
                        .id("After")
                        .name("After")
                        .expression("lead(count(), 1, " + windows + ")")
                        .build())
                .addColumns(Column.builder()
                        .id("__window__")
                        .name("__window__")
                        .expression("windowKey(" + windows + ")")
                        .group(0)
                        .visible(false)
                        .special(true)
                        .build())
                .build();
    }

    // The depth of every row of a table with all of its groups open.
    private List<Integer> windowRowDepths(final DataStore dataStore, final TableSettings tableSettings) {
        try {
            dataStore.getCompletionState().signalComplete();
            dataStore.getCompletionState().awaitCompletion();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        final ResultRequest closed = ResultRequest.builder()
                .componentId("componentX")
                .addMappings(tableSettings)
                .requestedRange(new OffsetRange(0, 100))
                .build();
        final Set<String> openGroups = ((TableResult) new TableResultCreator().create(dataStore, closed))
                .getRows()
                .stream()
                .map(Row::getGroupKey)
                .collect(Collectors.toSet());
        final ResultRequest open = ResultRequest.builder()
                .componentId("componentX")
                .addMappings(tableSettings)
                .requestedRange(new OffsetRange(0, 100))
                .openGroups(openGroups)
                .build();
        return ((TableResult) new TableResultCreator().create(dataStore, open))
                .getRows()
                .stream()
                .map(Row::getDepth)
                .toList();
    }

    // The store asks for the window's time field first, so it is the first value of a row.
    // Sends the rows of a node's store to another store, as a search across nodes does.
    private void transferPayloads(final DataStore node, final DataStore destination) throws InterruptedException {
        node.getCompletionState().signalComplete();
        boolean complete = false;
        for (int i = 0; i < 1_000 && !complete; i++) {
            // The node's store is complete once its last payload has been taken.
            complete = node.getCompletionState().awaitCompletion(10, TimeUnit.MILLISECONDS);
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (final Output output = new Output(outputStream)) {
                node.writePayload(output);
            }
            try (final Input input = new Input(new ByteArrayInputStream(outputStream.toByteArray()))) {
                destination.readPayload(input);
            }
        }
        assertThat(complete).isTrue();
    }

    private void addEvent(final DataStore dataStore, final String time, final String userId) {
        dataStore.accept(Val.of(ValDate.create(Instant.parse(time).toEpochMilli()), ValString.create(userId)));
    }

    private List<List<String>> windowRows(final DataStore dataStore, final TableSettings tableSettings) {
        return windowRows(dataStore, tableSettings, 4);
    }

    private List<List<String>> windowRows(final DataStore dataStore,
                                          final TableSettings tableSettings,
                                          final int columns) {
        try {
            dataStore.getCompletionState().signalComplete();
            dataStore.getCompletionState().awaitCompletion();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        final ResultRequest tableResultRequest = ResultRequest.builder()
                .componentId("componentX")
                .addMappings(tableSettings)
                .requestedRange(new OffsetRange(0, 100))
                .build();
        final TableResult tableResult = (TableResult) new TableResultCreator().create(dataStore, tableResultRequest);
        return tableResult
                .getRows()
                .stream()
                .map(row -> row.getValues().subList(0, columns))
                .toList();
    }

    @Test
    void testBigValues() {
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
                .build();

        final DataStore dataStore = create(tableSettings);

        SimpleMetrics.setEnabled(true);
        SimpleMetrics.measure("Added data", () -> {
            for (int i = 0; i < 300_000; i++) {
                final Val val = ValString.create("Text " + i + "test".repeat(1000));
                dataStore.accept(Val.of(val, val));
            }

            // Wait for all items to be added.
            try {
                dataStore.getCompletionState().signalComplete();
                dataStore.getCompletionState().awaitCompletion();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
        SimpleMetrics.report();

        SimpleMetrics.measure("Retrieved data", () -> {
            // Make sure we only get 50 results.
            final ResultRequest tableResultRequest = ResultRequest.builder()
                    .componentId("componentX")
                    .addMappings(tableSettings)
                    .requestedRange(new OffsetRange(0, 3000))
                    .build();
            final TableResultCreator tableComponentResultCreator = new TableResultCreator();
            final TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                    dataStore,
                    tableResultRequest);
            assertThat(searchResult.getResultRange().getLength()).isEqualTo(50);
            assertThat(searchResult.getTotalResults().intValue()).isEqualTo(50);
        });
        SimpleMetrics.report();
    }

    @Disabled
    @Test
    void testMultiThread2() {
        final int threadCount = 100;
        final Executor executor = Executors.newFixedThreadPool(threadCount * 2);
        final CompletableFuture<?>[] writers = new CompletableFuture[threadCount];
        for (int i = 0; i < threadCount; i++) {
            writers[i] = CompletableFuture.runAsync(this::testMultiThread, executor);
        }
        CompletableFuture.allOf(writers).join();
    }

    @Disabled
    @Test
    void testMultiThread() {
        final int threadCount = 100;
        final FormatterFactory formatterFactory = new FormatterFactory(null);

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
                        .group(1)
                        .build())
                .addColumns(Column.builder()
                        .id("Text2")
                        .name("Text2")
                        .expression("first(" + ParamUtil.create("Text2") + ")")
                        .format(Format.TEXT)
                        .build())
                .addColumns(Column.builder()
                        .id("count")
                        .name("count")
                        .expression("count()")
                        .format(Format.NUMBER)
                        .build())
                .addColumns(Column.builder()
                        .id("countGroups")
                        .name("countGroups")
                        .expression("countGroups()")
                        .format(Format.NUMBER)
                        .build())
                .showDetail(true)
                .build();

        final DataStore dataStore = create(tableSettings);

        // Start reading data store.
        final AtomicBoolean reading = new AtomicBoolean(true);
        final CompletableFuture<?>[] readers = new CompletableFuture[threadCount];
        final CompletableFuture<?>[] writers = new CompletableFuture[threadCount];
        final Executor executor = Executors.newFixedThreadPool(threadCount * 2);

        for (int j = 0; j < threadCount; j++) {
            final int run = j;
            readers[j] = CompletableFuture.runAsync(() -> {
                LOGGER.info(() -> "Reading " + run);
                while (reading.get()) {
                    final ResultRequest tableResultRequest = ResultRequest.builder()
                            .componentId("componentX")
                            .addMappings(tableSettings)
                            .requestedRange(new OffsetRange(0, 3000))
                            .build();
                    final TableResultCreator tableComponentResultCreator = new TableResultCreator(
                            formatterFactory,
                            new ExpressionPredicateFactory());
                    final TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                            dataStore,
                            tableResultRequest);
                    final Set<String> openGroups = searchResult
                            .getRows()
                            .stream()
                            .map(Row::getGroupKey)
                            .collect(Collectors.toSet());

                    final ResultRequest tableResultRequest2 = ResultRequest.builder()
                            .componentId("componentX")
                            .addMappings(tableSettings)
                            .requestedRange(new OffsetRange(0, 3000))
                            .openGroups(openGroups)
                            .build();

//                    if (!openGroups.isEmpty()) {
//                        LOGGER.info("Open groups: " + openGroups.size());
//                    }

                    final TableResultCreator tableComponentResultCreator2 = new TableResultCreator(
                            formatterFactory,
                            new ExpressionPredicateFactory());
                    final TableResult searchResult2 = (TableResult) tableComponentResultCreator2.create(
                            dataStore,
                            tableResultRequest2);


                    final Set<String> openGroups2 = searchResult2
                            .getRows()
                            .stream()
                            .map(Row::getGroupKey)
                            .collect(Collectors.toSet());

                    final ResultRequest tableResultRequest3 = ResultRequest.builder()
                            .componentId("componentX")
                            .addMappings(tableSettings)
                            .requestedRange(new OffsetRange(0, 3000))
                            .openGroups(openGroups2)
                            .build();

//                    if (!openGroups.isEmpty()) {
//                        LOGGER.info("Open groups: " + openGroups.size());
//                    }

                    final TableResultCreator tableComponentResultCreator3 = new TableResultCreator(
                            formatterFactory,
                            new ExpressionPredicateFactory());
                    final TableResult searchResult3 = (TableResult) tableComponentResultCreator2.create(
                            dataStore,
                            tableResultRequest3);
                }
            }, executor);
        }

        for (int j = 0; j < threadCount; j++) {
            final int run = j;
            writers[j] = CompletableFuture.runAsync(() -> {
                LOGGER.info(() -> "Writing " + run);
                for (int i = 0; i < 1_000_00; i++) {
                    final Val val1 = ValString.create("Text " + run);
                    final Val val2 = ValString.create("Text " + i);
                    dataStore.accept(Val.of(val1, val2));
                }
            }, executor);
        }
        CompletableFuture.allOf(writers).join();

        // Wait for all items to be added.
        try {
            dataStore.getCompletionState().signalComplete();
            dataStore.getCompletionState().awaitCompletion();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        reading.set(false);
        CompletableFuture.allOf(readers).join();

        SimpleMetrics.measure("Retrieved data", () -> {
            final ResultRequest tableResultRequest = ResultRequest.builder()
                    .componentId("componentX")
                    .addMappings(tableSettings)
                    .requestedRange(new OffsetRange(0, 3000))
                    .build();
            final TableResultCreator tableComponentResultCreator = new TableResultCreator(
                    formatterFactory,
                    new ExpressionPredicateFactory());
            final TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                    dataStore,
                    tableResultRequest);
        });
        SimpleMetrics.report();
    }

    @Test
    void testReload() throws Exception {
        final FormatterFactory formatterFactory = new FormatterFactory(null);

        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(Column.builder()
                        .id("StreamId")
                        .name("StreamId")
                        .expression(ParamUtil.create("StreamId"))
                        .format(Format.NUMBER)
                        .build())
                .addColumns(Column.builder()
                        .id("EventId")
                        .name("EventId")
                        .expression(ParamUtil.create("EventId"))
                        .format(Format.NUMBER)
                        .build())
                .addColumns(Column.builder()
                        .id("EventTime")
                        .name("EventTime")
                        .expression(ParamUtil.create("EventTime"))
                        .format(Format.DATE_TIME)
                        .build())
                .build();

        final QueryKey queryKey = new QueryKey(UUID.randomUUID().toString());
        final SearchResultStoreConfig resultStoreConfig = createResultStoreConfig();
        final DataStoreSettings dataStoreSettings = DataStoreSettings.createAnalyticStoreSettings();
        final SearchRequestSource searchRequestSource = SearchRequestSource
                .builder()
                .sourceType(SourceType.TABLE_BUILDER_ANALYTIC)
                .build();
        final LmdbDataStore dataStore = record((LmdbDataStore)
                create(
                        searchRequestSource,
                        queryKey,
                        "0",
                        tableSettings,
                        resultStoreConfig,
                        dataStoreSettings,
                        "reload"));

        for (int i = 1; i <= 100; i++) {
            for (int j = 1; j <= 100; j++) {
                final Val streamId = ValLong.create(i);
                final Val eventId = ValLong.create(j);
                final Val eventTime = ValLong.create(System.currentTimeMillis());
                dataStore.accept(Val.of(streamId, eventId, eventTime));
            }
        }

        // Wait for all items to be added.
        CurrentDbState currentDbState = dataStore.sync();
        assertThat(currentDbState.getStreamId()).isEqualTo(100);
        assertThat(currentDbState.getEventId()).isEqualTo(100);

        // Make sure we only get 50 results.
        final ResultRequest tableResultRequest = ResultRequest.builder()
                .componentId("0")
                .addMappings(tableSettings)
                .requestedRange(new OffsetRange(0, 50))
                .build();
        final TableResultCreator tableComponentResultCreator = new TableResultCreator(
                formatterFactory,
                new ExpressionPredicateFactory());
        TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                dataStore,
                tableResultRequest);
        assertThat(searchResult.getResultRange().getLength()).isEqualTo(50);
        assertThat(searchResult.getTotalResults().intValue()).isEqualTo(10000);

        // Close the db.
        dataStore.getCompletionState().signalComplete();
        dataStore.getCompletionState().awaitCompletion();
        dataStore.close();

        // Try and open the datastore again. Recorded after dataStore, so teardown clears it first
        // (newest first) - it holds the "reload" env dir open that dataStore's clear() deletes.
        final LmdbDataStore dataStore2 = record((LmdbDataStore)
                create(
                        searchRequestSource,
                        queryKey,
                        "0",
                        tableSettings,
                        resultStoreConfig,
                        dataStoreSettings,
                        "reload"));

        currentDbState = dataStore2.sync();
        assertThat(currentDbState.getStreamId()).isEqualTo(100);
        assertThat(currentDbState.getEventId()).isEqualTo(100);

        // Make sure we only get 50 results.
        searchResult = (TableResult) tableComponentResultCreator.create(
                dataStore2,
                tableResultRequest);
        assertThat(searchResult.getResultRange().getLength()).isEqualTo(50);
        assertThat(searchResult.getTotalResults().intValue()).isEqualTo(10000);

        // Load some more data.
        for (int i = 101; i <= 200; i++) {
            for (int j = 101; j <= 200; j++) {
                final Val streamId = ValLong.create(i);
                final Val eventId = ValLong.create(j);
                final Val eventTime = ValLong.create(System.currentTimeMillis());
                dataStore2.accept(Val.of(streamId, eventId, eventTime));
            }
        }

        // Wait for all items to be added.
        currentDbState = dataStore2.sync();
        assertThat(currentDbState.getStreamId()).isEqualTo(200);
        assertThat(currentDbState.getEventId()).isEqualTo(200);

        // Make sure we still only get 50 results.
        searchResult = (TableResult) tableComponentResultCreator.create(
                dataStore2,
                tableResultRequest);
        assertThat(searchResult.getResultRange().getLength()).isEqualTo(50);
        assertThat(searchResult.getTotalResults().intValue()).isEqualTo(20000);

        // dataStore2 is recorded, so teardown closes it before the @TempDir is deleted.
    }

    @Test
    void basicTest() {
        super.basicTest();
    }

    @Test
    void nestedTest() {
        super.nestedTest();
    }

    @Test
    void noValuesTest() {
        super.noValuesTest();
    }

    @Test
    void sortedTextTest() {
        super.sortedTextTest();
    }

    @Test
    void sortedNumberTest() {
        super.sortedNumberTest();
    }

    @Test
    void sortedCountedTextTest1() {
        super.sortedCountedTextTest1();
    }

    @Test
    void sortedCountedTextTest2() {
        super.sortedCountedTextTest2();
    }

    @Test
    void sortedCountedTextTest3() {
        super.sortedCountedTextTest3();
    }

    @Test
    void firstLastSelectorTest() {
        super.firstLastSelectorTest();
    }
}
