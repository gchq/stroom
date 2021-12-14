package stroom.docstore.impl.db;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class DocStoreConfig extends AbstractConfig implements HasDbConfig {

    private final DocStoreDbConfig dbConfig;

    public DocStoreConfig() {
        dbConfig = new DocStoreDbConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public DocStoreConfig(@JsonProperty("db") final DocStoreDbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @Override
    @JsonProperty("db")
    public DocStoreDbConfig getDbConfig() {
        return dbConfig;
    }

    @BootStrapConfig
    public static class DocStoreDbConfig extends AbstractDbConfig {

        public DocStoreDbConfig() {
            super();
        }

        @SuppressWarnings("unused")
        @JsonCreator
        public DocStoreDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
