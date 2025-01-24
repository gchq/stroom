package stroom.query.common.v2;

import stroom.datasource.api.v2.DataSourceProvider;

import java.util.Set;

public interface AdditionalDataSourceProviders {

    Set<DataSourceProvider> getDataSourceProviders();

}
