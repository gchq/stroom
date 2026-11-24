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
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestFlatResultCreator {

    private static final String COMPONENT_ID = "vis";

    private final ExpressionContext expressionContext = new ExpressionContext();
    private final TableSettings parent = table();
    private final TableSettings child = table();

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
