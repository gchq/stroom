package stroom.query.impl;

import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.docref.DocRef;
import stroom.docref.StringMatch.MatchType;
import stroom.query.shared.CompletionValue;
import stroom.query.shared.CompletionsRequest;
import stroom.query.shared.InsertType;
import stroom.query.shared.QueryHelpDetail;
import stroom.query.shared.QueryHelpField;
import stroom.query.shared.QueryHelpRequest;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpType;
import stroom.util.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.ResultPage.ResultConsumer;
import stroom.util.string.StringMatcher;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Singleton
public class Fields {

    public static final String FIELDS_ID = "fields";
    public static final String FIELDS_PARENT = FIELDS_ID + ".";

    private static final QueryHelpRow ROOT = QueryHelpRow.builder()
            .type(QueryHelpType.TITLE)
            .id(FIELDS_ID)
            .hasChildren(true)
            .title("Fields")
            .build();
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
                    final FindFieldInfoCriteria criteria = new FindFieldInfoCriteria(
                            new PageRequest(0, 1),
                            Collections.emptyList(),
                            optional.get(),
                            request.getStringMatch());
                    hasChildren = queryService.getFieldInfo(criteria).size() > 0;
                }

                final StringMatcher stringMatcher = new StringMatcher(request.getStringMatch());
                if (hasChildren ||
                        MatchType.ANY.equals(stringMatcher.getMatchType()) ||
                        stringMatcher.match(ROOT.getTitle()).isPresent()) {
                    resultConsumer.add(ROOT.copy().hasChildren(hasChildren).build());
                }

            } else if (request.getParentPath().startsWith(FIELDS_PARENT) && optional.isPresent()) {
                // Figure out if there are children.
                final FindFieldInfoCriteria criteria = new FindFieldInfoCriteria(
                        new PageRequest(request.getPageRequest().getOffset(),
                                request.getPageRequest().getLength() + 1),
                        request.getSortList(),
                        optional.get(),
                        request.getStringMatch());
                final ResultPage<FieldInfo> resultPage = queryService.getFieldInfo(criteria);
                resultConsumer.skip(resultPage.getPageStart());
                resultPage.getValues().forEach(fieldInfo -> {
                    final QueryHelpRow row = new QueryHelpRow(
                            QueryHelpType.FIELD,
                            "fields." + fieldInfo.getFldName(),
                            false,
                            null,
                            fieldInfo.getFldName(),
                            new QueryHelpField(fieldInfo));
                    resultConsumer.add(row);
                });
            }
        }
    }

    public void addCompletions(final CompletionsRequest request,
                               final PageRequest pageRequest,
                               final List<CompletionValue> resultList) {
        final QueryService queryService = queryServiceProvider.get();
        final Optional<DocRef> optional = Optional.ofNullable(request.getDataSourceRef())
                .or(() -> queryService.getReferencedDataSource(request.getText()));
        optional.ifPresent(docRef -> {
            final FindFieldInfoCriteria criteria = new FindFieldInfoCriteria(
                    pageRequest,
                    request.getSortList(),
                    docRef,
                    request.getStringMatch());

            final ResultPage<FieldInfo> resultPage = queryService.getFieldInfo(criteria);
            resultPage.getValues().forEach(fieldInfo -> resultList.add(createCompletionValue(fieldInfo)));
        });
    }

    private CompletionValue createCompletionValue(final FieldInfo fieldInfo) {
        final String insertText = getInsertText(fieldInfo.getFldName());
        final String tooltip = getDetail(fieldInfo);
        return new CompletionValue(
                fieldInfo.getFldName(),
                insertText,
                300,
                "Field",
                tooltip);
    }

    private String getDetail(final FieldInfo fieldInfo) {
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

    private void addFieldDetails(final DetailBuilder detail, final FieldInfo field) {
        final String fieldName = field.getFldName();
        final String fieldType = field.getFldType().getDisplayValue();
        final String supportedConditions = field.getConditions().toString();

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
            final FieldInfo fieldInfo = queryHelpField.getFieldInfo();
            final InsertType insertType = NullSafe.isBlankString(row.getTitle())
                    ? InsertType.BLANK
                    : InsertType.PLAIN_TEXT;
            final String insertText = getInsertText(row.getTitle());
            final String documentation = getDetail(fieldInfo);
            return Optional.of(new QueryHelpDetail(insertType, insertText, documentation));
        }

        return Optional.empty();
    }
}
