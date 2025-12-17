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

package stroom.query.impl;

import stroom.annotation.shared.AnnotationDecorationFields;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.shared.CompletionItem;
import stroom.query.shared.CompletionValue;
import stroom.query.shared.CompletionsRequest;
import stroom.query.shared.InsertType;
import stroom.query.shared.QueryHelpDetail;
import stroom.query.shared.QueryHelpField;
import stroom.query.shared.QueryHelpRequest;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpType;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.resultpage.ResultPageBuilder;
import stroom.util.shared.PageRequest;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Singleton
public class AnnotationFields {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationFields.class);

    public static final String FIELDS_ID = "annotation_fields";
    public static final String FIELDS_PARENT = FIELDS_ID + ".";

    private static final QueryHelpRow ROOT = QueryHelpRow.builder()
            .type(QueryHelpType.TITLE)
            .id(FIELDS_ID)
            .hasChildren(true)
            .title("Annotation Fields")
            .build();
    public static final int INITIAL_SCORE = 300;
    private final Provider<QueryService> queryServiceProvider;
    private final ExpressionPredicateFactory expressionPredicateFactory;

    @Inject
    AnnotationFields(final Provider<QueryService> queryServiceProvider,
                     final ExpressionPredicateFactory expressionPredicateFactory) {
        this.queryServiceProvider = queryServiceProvider;
        this.expressionPredicateFactory = expressionPredicateFactory;
    }

    private boolean allowDecoration(final DocRef dataSourceRef, final String query) {
        final QueryService queryService = queryServiceProvider.get();
        final Optional<DocRef> optionalDocRef = Optional.ofNullable(dataSourceRef)
                .or(() -> queryService.getReferencedDataSource(query));
        return optionalDocRef
                .map(DocRef::getType)
                .map(type -> type.equals(LuceneIndexDoc.TYPE) ||
                             type.equals(ElasticIndexDoc.TYPE) ||
                             type.equals(SolrIndexDoc.TYPE))
                .orElse(false);
    }

    public void addRows(final QueryHelpRequest request,
                        final ResultPageBuilder<QueryHelpRow> resultPageBuilder) {
        final PageRequest pageRequest = request.getPageRequest();
        if (pageRequest.getLength() > 0) {
            final boolean allowDecoration = allowDecoration(request.getDataSourceRef(), request.getQuery());

            final Predicate<String> predicate = expressionPredicateFactory.create(request.getFilter());
            if (request.getParentPath().isBlank()) {
                if (allowDecoration ||
                    predicate.test(ROOT.getTitle())) {
                    resultPageBuilder.add(ROOT.copy().hasChildren(allowDecoration).build());
                }

            } else if (request.getParentPath().startsWith(FIELDS_PARENT) && allowDecoration) {
                final List<QueryField> fields = AnnotationDecorationFields.DECORATION_FIELDS;
                fields
                        .stream()
                        .filter(field -> predicate.test(field.getFldName()))
                        .forEach(field -> {
                            final QueryHelpRow row = new QueryHelpRow(
                                    QueryHelpType.FIELD,
                                    FIELDS_PARENT + field.getFldName(),
                                    false,
                                    null,
                                    null,
                                    field.getFldName(),
                                    new QueryHelpField(field));
                            resultPageBuilder.add(row);
                        });
            }
        }
    }

    public void addCompletions(final CompletionsRequest request,
                               final int maxCompletions,
                               final List<CompletionItem> resultList,
                               final Boolean queryable) {
        if (queryable == null || !queryable) {
            try {
                final boolean allowDecoration = allowDecoration(request.getDataSourceRef(), request.getText());
                if (allowDecoration) {
                    final List<QueryField> fields = AnnotationDecorationFields.DECORATION_FIELDS;
                    fields.stream()
                            .map(row -> createCompletionValue(row, INITIAL_SCORE))
                            .forEach(resultList::add);
                }
            } catch (final Exception e) {
                LOGGER.error("Error adding field completions: {}", e.getMessage(), e);
            }
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
            final String documentation = "A list of annotation decoration fields available to 'select'. " +
                                         "The fields will only become available once a suitable data source has been " +
                                         "specified using the 'from' keyword.";
            return Optional.of(new QueryHelpDetail(insertType, null, documentation));

        } else if (row.getId().startsWith(FIELDS_PARENT) && row.getData() instanceof
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
