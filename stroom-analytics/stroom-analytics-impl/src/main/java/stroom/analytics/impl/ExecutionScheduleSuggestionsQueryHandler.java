/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.ExecutionScheduleFields;
import stroom.analytics.shared.ReportDoc;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.Suggestions;
import stroom.suggestions.api.SuggestionsQueryHandler;
import stroom.util.shared.scheduler.ScheduleType;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class ExecutionScheduleSuggestionsQueryHandler implements SuggestionsQueryHandler {

    private static final int LIMIT = 20;

    private final ExpressionPredicateFactory expressionPredicateFactory;

//    private final Map<String, Function<String, List<String>>> indexShardsFieldNameToFunctionMap = Map.of(
//            ExecutionScheduleFields.FIELD_NAME.getFldName(), filter ->
//                    getNonUniqueDocRefNames(ExecutionScheduleFields.TYPE, filter));

    @Inject
    ExecutionScheduleSuggestionsQueryHandler(final ExpressionPredicateFactory expressionPredicateFactory) {
        this.expressionPredicateFactory = expressionPredicateFactory;
    }

    private final Map<String, Function<String, List<String>>> fieldNameToFunctionMap = Map.of(
            ExecutionScheduleFields.FIELD_SCHEDULE_TYPE.getFldName(), this::createScheduleTypeList,
            ExecutionScheduleFields.FIELD_PARENT_DOC_TYPE.getFldName(), this::createParentDocTypeList
    );

    @Override
    public Suggestions getSuggestions(final FetchSuggestionsRequest request) {
        List<String> result = Collections.emptyList();

        final String fieldName = request.getField().getFldName();
        final Function<String, List<String>> suggestionFunc = fieldNameToFunctionMap.get(fieldName);

        if (suggestionFunc != null) {
            result = suggestionFunc.apply(request.getText());
        }

        final boolean cache = request.getText() == null || request.getText().isBlank();

        return new Suggestions(result, cache);
    }

    @NotNull
    private List<String> createScheduleTypeList(final String userInput) {
        return expressionPredicateFactory.filterAndSortStream(
                        Arrays.stream(ScheduleType.values())
                                .map(ScheduleType::getDisplayValue),
                        userInput,
                        Optional.of(Comparator.naturalOrder()))
                .limit(LIMIT)
                .toList();
    }

    @NotNull
    private List<String> createParentDocTypeList(final String userInput) {
        return expressionPredicateFactory.filterAndSortStream(
                        java.util.stream.Stream.of(AnalyticRuleDoc.TYPE, ReportDoc.TYPE),
                        userInput,
                        Optional.of(Comparator.naturalOrder()))
                .limit(LIMIT)
                .toList();
    }
}
