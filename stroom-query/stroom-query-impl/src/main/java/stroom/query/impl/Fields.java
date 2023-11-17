package stroom.query.impl;

import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.docref.DocRef;
import stroom.query.shared.QueryHelpField;
import stroom.query.shared.QueryHelpRequest;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpTitle;
import stroom.query.shared.QueryHelpType;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.ResultPage.ResultConsumer;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Collections;
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
            .data(new QueryHelpTitle(
                    "A list of the fields available to 'select' from the specified data source. " +
                            "The fields will only become available one the data source has been " +
                            "specified using the 'from' keyword."))
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
                resultConsumer.add(ROOT.copy().hasChildren(hasChildren).build());

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
                            "fields." + fieldInfo.getFieldName(),
                            false,
                            null,
                            fieldInfo.getFieldName(),
                            new QueryHelpField(fieldInfo));
                    resultConsumer.add(row);
                });
            }
        }
    }
}
