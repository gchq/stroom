/*
 * Copyright 2026 Crown Copyright
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
import stroom.query.api.DateTimeSettings;
import stroom.query.api.FlatResult;
import stroom.query.api.Format;
import stroom.query.api.ParamUtil;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.TableSettings;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValDuration;
import stroom.query.language.functions.ValErr;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValPeriod;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestFlatResultCreator {

    private static final String COMPONENT_ID = "vis";

    private final ExpressionContext expressionContext = new ExpressionContext();
    private final TableSettings parent = table();
    private final TableSettings child = table();

    @Test
    void durationsAreMilliseconds() {
        final Column column = Column.builder().format(Format.DURATION).build();
        assertThat(FlatResultCreator.convert(column, ValDuration.create(1_500L))).isEqualTo(1_500D);
        assertThat(FlatResultCreator.convert(column, ValLong.create(1_500L))).isEqualTo(1_500D);
        assertThat(FlatResultCreator.convert(column, ValString.create("-1.5s"))).isEqualTo(-1_500D);
        assertThat(FlatResultCreator.convert(column, ValPeriod.create(0, 0, 1))).isEqualTo(86_400_000D);
        assertThat(FlatResultCreator.convert(column, ValString.create("soon"))).isNull();
        assertThat(FlatResultCreator.convert(column, ValNull.INSTANCE)).isNull();
        assertThat(FlatResultCreator.convert(column, ValErr.create("bad"))).isNull();
    }

    // Those that are different must stay so, or their rows would be added together.
    @Test
    void durationGroups() {
        final Column column = Column.builder().format(Format.DURATION).build();
        assertThat(FlatResultCreator.convertGroup(column, ValDuration.create(1_500L))).isEqualTo(1_500D);
        assertThat(FlatResultCreator.convertGroup(column, ValLong.create(1_500L))).isEqualTo(1_500D);
        assertThat(FlatResultCreator.convertGroup(column, ValInteger.create(1_500))).isEqualTo(1_500D);
        assertThat(FlatResultCreator.convertGroup(column, ValDouble.create(1_500))).isEqualTo(1_500D);
        assertThat(FlatResultCreator.convertGroup(column, ValDouble.create(1.2))).isEqualTo(1.2);
        assertThat(FlatResultCreator.convertGroup(column, ValDouble.create(1e300))).isEqualTo(1e300);
        assertThat(FlatResultCreator.convertGroup(column, ValFloat.create(1.5F))).isEqualTo(1.5F);
        // Not a number, which would be a long duration.
        assertThat(FlatResultCreator.convertGroup(column, ValDate.create(5))).isEqualTo("1970-01-01T00:00:00.005Z");
        assertThat(FlatResultCreator.convertGroup(column, ValBoolean.create(true))).isEqualTo("true");
        assertThat(FlatResultCreator.convertGroup(column, ValErr.create("bad"))).isEqualTo("ERR: bad");
        for (final String text : List.of("1h", "60m", "soon", "later", "1500")) {
            assertThat(FlatResultCreator.convertGroup(column, ValString.create(text))).isEqualTo(text);
        }
        assertThat(FlatResultCreator.convertGroup(column, ValPeriod.create(0, 0, 1))).isEqualTo("P1D");
        assertThat(FlatResultCreator.convertGroup(column, ValNull.INSTANCE)).isNull();
    }

    @Test
    void groupsOfOtherFormats() {
        assertThat(FlatResultCreator.convertGroup(
                Column.builder().format(Format.NUMBER).build(), ValString.create("60m"))).isEqualTo(3_600_000D);
        assertThat(FlatResultCreator.convertGroup(
                Column.builder().format(Format.TEXT).build(), ValLong.create(5))).isEqualTo("5");
        assertThat(FlatResultCreator.convertGroup(null, ValLong.create(5))).isEqualTo(5L);
    }

    @Test
    void otherFormats() {
        assertThat(FlatResultCreator.convert(
                Column.builder().format(Format.NUMBER).build(), ValDouble.create(1.5))).isEqualTo(1.5);
        assertThat(FlatResultCreator.convert(
                Column.builder().format(Format.TEXT).build(), ValDuration.create(1_500L))).isEqualTo("1s500ms");
        assertThat(FlatResultCreator.convert(
                Column.builder().format(Format.GENERAL).build(), ValDuration.create(1_500L))).isEqualTo(1_500L);
        assertThat(FlatResultCreator.convert(
                Column.builder().build(), ValString.create("1.5s"))).isEqualTo("1.5s");
    }

    // The same creator is used each time the result is asked for.
    @Test
    void testWarningDoesNotStopLaterResults() {
        final FlatResultCreator creator = create(errorConsumer ->
                errorConsumer.add(Severity.WARNING, () -> "a warning"));
        for (int i = 0; i < 3; i++) {
            final FlatResult result = createResult(creator);
            assertThat(result.getValues()).hasSize(1);
            assertThat(result.getStructure()).isNotEmpty();
            assertThat(result.getErrorMessages()).hasSize(1);
        }
    }

    // Only 100 messages are kept, so the warnings of earlier results must not leave no room for an error.
    @Test
    void testErrorAfterManyWarnings() {
        final AtomicInteger count = new AtomicInteger();
        final FlatResultCreator creator = create(errorConsumer -> {
            errorConsumer.add(Severity.WARNING, () -> "a warning");
            if (count.incrementAndGet() == 151) {
                throw new IllegalStateException("an error");
            }
        });
        for (int i = 0; i < 150; i++) {
            assertThat(createResult(creator).getErrorMessages()).hasSize(1);
        }
        for (int i = 0; i < 3; i++) {
            final FlatResult result = createResult(creator);
            assertThat(result.getValues()).isNullOrEmpty();
            assertThat(result.getErrorMessages().toString()).contains("an error");
        }
        // There was no point in trying again.
        assertThat(count.get()).isEqualTo(151);
    }

    @Test
    void testErrorStopsLaterResults() {
        final FlatResultCreator creator = create(errorConsumer ->
                errorConsumer.add(Severity.ERROR, () -> "an error"));
        assertThat(createResult(creator).getValues()).hasSize(1);
        final FlatResult result = createResult(creator);
        assertThat(result.getValues()).isNullOrEmpty();
        assertThat(result.getErrorMessages()).hasSize(1);
    }

    private FlatResult createResult(final FlatResultCreator creator) {
        final MapDataStore parentStore = store(parent, new ErrorConsumerImpl());
        parentStore.accept(Val.of(ValString.create("a")));
        return (FlatResult) creator.create(parentStore, ResultRequest.builder()
                .componentId(COMPONENT_ID)
                .addMappings(parent)
                .addMappings(child)
                .fetch(Fetch.ALL)
                .build());
    }

    private FlatResultCreator create(final Consumer<ErrorConsumer> onCreateChildStore) {
        final DataStoreFactory dataStoreFactory = Mockito.mock(DataStoreFactory.class);
        Mockito.when(dataStoreFactory.create(
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    final ErrorConsumer errorConsumer = invocation.getArgument(8);
                    onCreateChildStore.accept(errorConsumer);
                    return store(invocation.getArgument(4), errorConsumer);
                });
        return new FlatResultCreator(
                dataStoreFactory,
                new SearchRequest(
                        SearchRequestSource.createBasic(),
                        new QueryKey("test"),
                        Query.builder().build(),
                        null,
                        DateTimeSettings.builder().build(),
                        false),
                COMPONENT_ID,
                expressionContext,
                Map.of(),
                null,
                new ExpressionPredicateFactory(),
                Sizes.unlimited(),
                false,
                () -> Runnable::run);
    }

    private MapDataStore store(final TableSettings tableSettings, final ErrorConsumer errorConsumer) {
        final FieldIndex fieldIndex = new FieldIndex();
        return new MapDataStore(
                COMPONENT_ID,
                tableSettings,
                expressionContext,
                fieldIndex,
                Map.of(),
                DataStoreSettings.createBasicSearchResultStoreSettings(),
                errorConsumer,
                new ResultStoreMapConfig(),
                () -> Runnable::run);
    }

    private static TableSettings table() {
        return TableSettings.builder()
                .addColumns(Column.builder()
                        .id("Value")
                        .name("Value")
                        .expression(ParamUtil.create("Value"))
                        .build())
                .build();
    }
}
