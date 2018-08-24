package stroom.index;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class IndexConfig {
    private int ramBufferSizeMB = 1024;
    private IndexWriterConfig indexWriterConfig;

    public IndexConfig() {
        this.indexWriterConfig = new IndexWriterConfig();
    }

    @JsonPropertyDescription("The amount of RAM Lucene can use to buffer when indexing in Mb")
    public int getRamBufferSizeMB() {
        return ramBufferSizeMB;
    }

    public void setRamBufferSizeMB(final int ramBufferSizeMB) {
        this.ramBufferSizeMB = ramBufferSizeMB;
    }

    @JsonProperty("writer")
    public IndexWriterConfig getIndexWriterConfig() {
        return indexWriterConfig;
    }

    public void setIndexWriterConfig(final IndexWriterConfig indexWriterConfig) {
        this.indexWriterConfig = indexWriterConfig;
    }
}
