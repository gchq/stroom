package stroom.query.client;

import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.datasource.shared.DataSourceResource;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.docstore.shared.Documentation;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.google.gwt.core.client.GWT;

import java.util.Optional;
import java.util.function.Consumer;
import javax.inject.Inject;

public class DataSourceClient {

    private static final DataSourceResource DATA_SOURCE_RESOURCE = GWT.create(DataSourceResource.class);

    private final RestFactory restFactory;

    @Inject
    public DataSourceClient(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void findFields(final FindFieldCriteria findFieldInfoCriteria,
                           final Consumer<ResultPage<QueryField>> consumer) {
        restFactory
                .create(DATA_SOURCE_RESOURCE)
                .method(res -> res.findFields(findFieldInfoCriteria))
                .onSuccess(consumer)
                .exec();
    }

    public void findFieldByName(final DocRef dataSourceRef,
                                final String fieldName,
                                final Boolean queryable,
                                final Consumer<QueryField> consumer) {
        if (dataSourceRef != null) {
            final FindFieldCriteria findFieldInfoCriteria = new FindFieldCriteria(
                    PageRequest.oneRow(),
                    null,
                    dataSourceRef,
                    StringMatch.equals(fieldName, true),
                    queryable);
            restFactory
                    .create(DATA_SOURCE_RESOURCE)
                    .method(res -> res.findFields(findFieldInfoCriteria))
                    .onSuccess(result -> {
                        if (result.getValues().size() > 0) {
                            consumer.accept(result.getFirst());
                        }
                    })
                    .exec();
        }
    }

    public void fetchDataSourceDescription(final DocRef dataSourceDocRef,
                                           final Consumer<Optional<String>> descriptionConsumer) {

        if (dataSourceDocRef != null) {
            restFactory
                    .create(DATA_SOURCE_RESOURCE)
                    .method(res -> res.fetchDocumentation(dataSourceDocRef))
                    .onSuccess(documentation -> {
                        final Optional<String> optMarkDown = GwtNullSafe.getAsOptional(documentation,
                                Documentation::getMarkdown);
                        if (descriptionConsumer != null) {
                            descriptionConsumer.accept(optMarkDown);
                        }
                    })
                    .exec();
        }
    }

    public void fetchDefaultExtractionPipeline(DocRef dataSourceRef, Consumer<DocRef> consumer) {
        restFactory
                .create(DATA_SOURCE_RESOURCE)
                .method(res -> res.fetchDefaultExtractionPipeline(dataSourceRef))
                .onSuccess(consumer)
                .exec();
    }
}
