package stroom.meta.impl;

import stroom.datasource.api.v2.DataSourceProvider;
import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.meta.shared.MetaFields;
import stroom.query.common.v2.FieldInfoResultPageBuilder;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Optional;

/**
 * This class provides a data source for the `StreamStore` source type as opposed to the generic `Searchable`.
 */
public class MetaDataSourceProvider implements DataSourceProvider {

    @Override
    public List<DocRef> list() {
        return List.of(MetaFields.STREAM_STORE_DOC_REF);
    }

    @Override
    public String getType() {
        return MetaFields.STREAM_STORE_DOC_REF.getType();
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        if (!MetaFields.STREAM_STORE_DOC_REF.equals(criteria.getDataSourceRef())) {
            return ResultPage.empty();
        }
        return FieldInfoResultPageBuilder.builder(criteria).addAll(MetaFields.getFields()).build();
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.empty();
    }

    @Override
    public DocRef fetchDefaultExtractionPipeline(final DocRef docRef) {
        return null;
    }
}
