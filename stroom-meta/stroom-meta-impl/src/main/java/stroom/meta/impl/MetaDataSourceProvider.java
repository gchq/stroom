package stroom.meta.impl;

import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DataSourceProvider;
import stroom.docref.DocRef;
import stroom.meta.shared.MetaFields;

/**
 * This class provides a data source for the `StreamStore` source type as opposed to the generic `Searchable`.
 */
public class MetaDataSourceProvider implements DataSourceProvider {

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        if (docRef.equals(MetaFields.STREAM_STORE_DOC_REF)) {
            return DataSource
                    .builder()
                    .docRef(MetaFields.STREAM_STORE_DOC_REF)
                    .fields(MetaFields.getFields())
                    .build();
        }
        return null;
    }

    @Override
    public String getType() {
        return MetaFields.STREAM_STORE_DOC_REF.getType();
    }
}
