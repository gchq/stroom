/*
 * Copyright 2024 Crown Copyright
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

package stroom.query.impl;

import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.docref.StringMatch.MatchType;
import stroom.query.shared.CompletionItem;
import stroom.query.shared.CompletionValue;
import stroom.query.shared.CompletionsRequest;
import stroom.query.shared.InsertType;
import stroom.query.shared.QueryHelpDetail;
import stroom.query.shared.QueryHelpField;
import stroom.query.shared.QueryHelpRequest;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpType;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.ResultPage.ResultConsumer;
import stroom.util.string.AceStringMatcher;
import stroom.util.string.AceStringMatcher.AceMatchResult;
import stroom.util.string.StringMatcher;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Singleton
public class Fields {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Fields.class);

    public static final String FIELDS_ID = "fields";
    public static final String FIELDS_PARENT = FIELDS_ID + ".";

    private static final QueryHelpRow ROOT = QueryHelpRow.builder()
            .type(QueryHelpType.TITLE)
            .id(FIELDS_ID)
            .hasChildren(true)
            .title("Fields")
            .build();
    public static final int INITIAL_SCORE = 300;
    private final Provider<QueryService> queryServiceProvider;

    @Inject
    Fields(final Provider<QueryService> queryServiceProvider) {
        this.queryServiceProvider = queryServiceProvider;
    }

    public void addRows(final QueryHelpRequest request,
                        final ResultConsumer<QueryHelpRow> resultConsumer) {
        final PageRequest pageRequest = request.getPageRequest();
        if (pageRequest.getLength() > 0) {
            final QueryService queryService = queryServiceProvider.get();
            final Optional<DocRef> optional = Optional.ofNullable(request.getDataSourceRef())
                    .or(() -> queryService.getReferencedDataSource(request.getQuery()));

            if (request.getParentPath().isBlank()) {
                // Figure out if there are children.
                boolean hasChildren = false;
                if (optional.isPresent()) {
                    final FindFieldCriteria criteria = new FindFieldCriteria(
                            PageRequest.oneRow(),
                            Collections.emptyList(),
                            optional.get(),
                            request.getStringMatch(),
                            null);
                    hasChildren = queryService.findFields(criteria).size() > 0;
                }

                final StringMatcher stringMatcher = new StringMatcher(request.getStringMatch());
                if (hasChildren ||
                        MatchType.ANY.equals(stringMatcher.getMatchType()) ||
                        stringMatcher.match(ROOT.getTitle()).isPresent()) {
                    resultConsumer.add(ROOT.copy().hasChildren(hasChildren).build());
                }

            } else if (request.getParentPath().startsWith(FIELDS_PARENT) && optional.isPresent()) {
                // Figure out if there are children.
                final FindFieldCriteria criteria = new FindFieldCriteria(
                        new PageRequest(request.getPageRequest().getOffset(),
                                request.getPageRequest().getLength() + 1),
                        request.getSortList(),
                        optional.get(),
                        request.getStringMatch(),
                        null);
                final ResultPage<QueryField> resultPage = queryService.findFields(criteria);
                resultConsumer.skip(resultPage.getPageStart());
                resultPage.getValues().forEach(fieldInfo -> {
                    final QueryHelpRow row = new QueryHelpRow(
                            QueryHelpType.FIELD,
                            "fields." + fieldInfo.getFldName(),
                            false,
                            null,
                            null,
                            fieldInfo.getFldName(),
                            new QueryHelpField(fieldInfo));
                    resultConsumer.add(row);
                });
            }
        }
    }

    public void addCompletions(final CompletionsRequest request,
                               final int maxCompletions,
                               final List<CompletionItem> resultList) {
        try {
            final QueryService queryService = queryServiceProvider.get();
            final Optional<DocRef> optDataSourceRef = Optional.ofNullable(request.getDataSourceRef())
                    .or(() -> queryService.getReferencedDataSource(request.getText()));

            optDataSourceRef.ifPresent(dataSourceRef -> {

                final int fieldCount = queryService.getFieldCount(dataSourceRef);

                // We don't want to push down the filter as ideally we want Ace to get all fields, so it can filter
                // them. We do however have to limit them as there could be 1000s of fields

                // This could potentially return many 1000s of fields

                if (fieldCount > maxCompletions) {
                    final String pattern = request.getPattern();

                    // If there is no pattern then we have no idea which items score better
                    // so, we may as well push down the limit, however if there is
                    // then get all data, so we can score in here.
                    final PageRequest pageRequest = NullSafe.isBlankString(pattern)
                            ? new PageRequest(0, maxCompletions)
                            : PageRequest.unlimited();
                    // More fields than the client wants so push down the filtering
                    final List<QueryField> fields = queryService.findFields(new FindFieldCriteria(
                                    pageRequest,
                                    null,
                                    dataSourceRef,
                                    new StringMatch(MatchType.CHARS_ANYWHERE, false, pattern),
                                    null))
                            .getValues();

                    // Score the matching fields so we get the best matches
                    final List<AceMatchResult<QueryField>> matchResults = AceStringMatcher.filterCompletions(fields,
                                    pattern,
                                    INITIAL_SCORE,
                                    QueryField::getFldName)
                            .stream()
                            .sorted(AceStringMatcher.SCORE_DESC_THEN_NAME_COMPARATOR)
                            .toList();

                    LOGGER.debug(() -> LogUtil.message("Found {} match results, from {} items, maxCompletions {}",
                            matchResults.size(), fields.size(), maxCompletions));

                    matchResults.stream()
                            .limit(maxCompletions)
                            .map(matchResult -> createCompletionValue(matchResult.item(), matchResult.score()))
                            .forEach(resultList::add);
                } else {
                    // Datasource has fewer fields than the limit so just get them all
                    final List<QueryField> fields = queryService.findFields(new FindFieldCriteria(
                                    PageRequest.unlimited(),
                                    null,
                                    dataSourceRef,
                                    StringMatch.any(),
                                    null))
                            .getValues();
                    LOGGER.debug(() -> LogUtil.message("Found {} match results using offset {}, maxCompletions {}",
                            fields.size(), maxCompletions));
                    fields.stream()
                            .map(row -> createCompletionValue(row, INITIAL_SCORE))
                            .forEach(resultList::add);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error adding field completions: {}", e.getMessage(), e);
        }
    }

    private CompletionValue createCompletionValue(final QueryField fieldInfo, final int score) {
        final String insertText = getInsertText(fieldInfo.getFldName());
        final String tooltip = getDetail(fieldInfo);
        return new CompletionValue(
                fieldInfo.getFldName(),
                insertText,
                score,
                "Field",
                tooltip);
    }

    private String getDetail(final QueryField fieldInfo) {
        final DetailBuilder detail = new DetailBuilder();
        detail.title(fieldInfo.getFldName());
        detail.description(description -> addFieldDetails(description, fieldInfo));
        return detail.build();
    }

    private String getInsertText(final String fieldName) {
        return fieldName.contains(" ")
                ? "${" + fieldName + "}"
                : fieldName;
    }

    private void addFieldDetails(final DetailBuilder detail, final QueryField field) {
        final String fieldName = field.getFldName();
        final String fieldType = field.getFldType().getDisplayValue();
        final String supportedConditions = field.getConditionSet().toString();

        detail.table(table -> table.appendKVRow("Name:", fieldName)
                .appendKVRow("Type:", fieldType)
                .appendKVRow("Supported Conditions:", supportedConditions)
                .appendKVRow("Is queryable:", asDisplayValue(field.queryable()))
                .appendKVRow("Is numeric:", asDisplayValue(field.getFldType().isNumeric())));
    }

    private String asDisplayValue(final boolean bool) {
        return bool
                ? "True"
                : "False";
    }

    public Optional<QueryHelpDetail> fetchDetail(final QueryHelpRow row) {
        if (FIELDS_ID.equals(row.getId())) {
            final InsertType insertType = InsertType.NOT_INSERTABLE;
            final String documentation = "A list of the fields available to 'select' from the specified data source. " +
                    "The fields will only become available one the data source has been " +
                    "specified using the 'from' keyword.";
            return Optional.of(new QueryHelpDetail(insertType, null, documentation));

        } else if (row.getId().startsWith(FIELDS_ID + ".") && row.getData() instanceof
                final QueryHelpField queryHelpField) {
            final QueryField fieldInfo = queryHelpField.getField();
            final InsertType insertType = InsertType.plainText(row.getTitle());
            final String insertText = getInsertText(row.getTitle());
            final String documentation = getDetail(fieldInfo);
            return Optional.of(new QueryHelpDetail(insertType, insertText, documentation));
        }

        return Optional.empty();
    }
}
