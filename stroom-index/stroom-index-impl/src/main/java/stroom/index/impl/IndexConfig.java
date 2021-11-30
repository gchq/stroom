package stroom.index.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;


public class IndexConfig extends AbstractConfig implements HasDbConfig {

    private final IndexDbConfig dbConfig;
    private final int ramBufferSizeMB;
    private final IndexWriterConfig indexWriterConfig;
    private final CacheConfig indexStructureCache;

    public IndexConfig() {
        dbConfig = new IndexDbConfig();
        ramBufferSizeMB = 1024;
        indexWriterConfig = new IndexWriterConfig();
        indexStructureCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(StroomDuration.ofSeconds(10))
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public IndexConfig(@JsonProperty("db") final IndexDbConfig dbConfig,
                       @JsonProperty("ramBufferSizeMB") final int ramBufferSizeMB,
                       @JsonProperty("writer") final IndexWriterConfig indexWriterConfig,
                       @JsonProperty("indexStructureCache") final CacheConfig indexStructureCache) {
        this.dbConfig = dbConfig;
        this.ramBufferSizeMB = ramBufferSizeMB;
        this.indexWriterConfig = indexWriterConfig;
        this.indexStructureCache = indexStructureCache;
    }

    @Override
    @JsonProperty("db")
    public IndexDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonPropertyDescription("The amount of RAM Lucene can use to buffer when indexing in Mb")
    public int getRamBufferSizeMB() {
        return ramBufferSizeMB;
    }

    @JsonProperty("writer")
    public IndexWriterConfig getIndexWriterConfig() {
        return indexWriterConfig;
    }

    public CacheConfig getIndexStructureCache() {
        return indexStructureCache;
    }

    @Override
    public String toString() {
        return "IndexConfig{" +
                "dbConfig=" + dbConfig +
                ", ramBufferSizeMB=" + ramBufferSizeMB +
                ", indexWriterConfig=" + indexWriterConfig +
                ", indexStructureCache=" + indexStructureCache +
                '}';
    }

    public static class IndexDbConfig extends AbstractDbConfig {

        public IndexDbConfig() {
            super();
        }

        @JsonCreator
        public IndexDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
