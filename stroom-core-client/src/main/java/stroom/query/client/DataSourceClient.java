package stroom.query.client;

import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
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

    public void findFields(final FindFieldInfoCriteria findFieldInfoCriteria,
                           final Consumer<ResultPage<FieldInfo>> consumer) {
        restFactory
                .builder()
                .forResultPageOf(FieldInfo.class)
                .onSuccess(consumer)
                .call(DATA_SOURCE_RESOURCE)
                .findFields(findFieldInfoCriteria);
    }

    public void findFieldByName(final DocRef dataSourceRef,
                                final String fieldName,
                                final Consumer<FieldInfo> consumer) {
        if (dataSourceRef != null) {
            final FindFieldInfoCriteria findFieldInfoCriteria = new FindFieldInfoCriteria(
                    new PageRequest(0, 1),
                    null,
                    dataSourceRef,
                    StringMatch.equals(fieldName, true));
            restFactory
                    .builder()
                    .forResultPageOf(FieldInfo.class)
                    .onSuccess(result -> {
                        if (result.getValues().size() > 0) {
                            consumer.accept(result.getFirst());
                        }
                    })
                    .call(DATA_SOURCE_RESOURCE)
                    .findFields(findFieldInfoCriteria);
        }
    }

    public void fetchDataSourceDescription(final DocRef dataSourceDocRef,
                                           final Consumer<Optional<String>> descriptionConsumer) {

        if (dataSourceDocRef != null) {
            restFactory
                    .builder()
                    .forType(Documentation.class)
                    .onSuccess(documentation -> {
                        final Optional<String> optMarkDown = GwtNullSafe.getAsOptional(documentation,
                                Documentation::getMarkdown);
                        if (descriptionConsumer != null) {
                            descriptionConsumer.accept(optMarkDown);
                        }
                    })
                    .call(DATA_SOURCE_RESOURCE)
                    .fetchDocumentation(dataSourceDocRef);
        }
    }

    public void fetchDefaultExtractionPipeline(DocRef dataSourceRef, Consumer<DocRef> consumer) {
        restFactory
                .builder()
                .forType(DocRef.class)
                .onSuccess(consumer)
                .call(DATA_SOURCE_RESOURCE)
                .fetchDefaultExtractionPipeline(dataSourceRef);
    }
}
