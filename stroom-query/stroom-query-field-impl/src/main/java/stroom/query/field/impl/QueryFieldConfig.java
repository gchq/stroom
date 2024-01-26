package stroom.query.field.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class QueryFieldConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private final QueryDatasourceDbConfig dbConfig;

    public QueryFieldConfig() {
        dbConfig = new QueryDatasourceDbConfig();
    }

    @JsonCreator
    public QueryFieldConfig(@JsonProperty("db") final QueryDatasourceDbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @Override
    @JsonProperty("db")
    public QueryDatasourceDbConfig getDbConfig() {
        return dbConfig;
    }

    @BootStrapConfig
    public static class QueryDatasourceDbConfig extends AbstractDbConfig {

        public QueryDatasourceDbConfig() {
            super();
        }

        @SuppressWarnings("unused")
        @JsonCreator
        public QueryDatasourceDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
