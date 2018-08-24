package stroom.benchmark;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class BenchmarkClusterConfig {
    private int streamCount = 1000;
    private int recordCount = 10000;
    private int concurrentWriters = 10;

    @JsonPropertyDescription("Set the number of streams to be created during a benchmark test")
    public int getStreamCount() {
        return streamCount;
    }

    public void setStreamCount(final int streamCount) {
        this.streamCount = streamCount;
    }

    @JsonPropertyDescription("Set the number of records to be created for each stream during a benchmark test")
    public int getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(final int recordCount) {
        this.recordCount = recordCount;
    }

    @JsonPropertyDescription("Set the number of threads to use concurrently to write test streams")
    public int getConcurrentWriters() {
        return concurrentWriters;
    }

    public void setConcurrentWriters(final int concurrentWriters) {
        this.concurrentWriters = concurrentWriters;
    }
}
