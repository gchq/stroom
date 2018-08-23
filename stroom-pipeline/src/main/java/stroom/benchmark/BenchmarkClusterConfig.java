package stroom.benchmark;

import stroom.properties.api.PropertyService;

public class BenchmarkClusterConfig {
    private final int streamCount;
    private final int recordCount;
    private final int concurrentWriters;

    BenchmarkClusterConfig(final PropertyService propertyService) {
        this.streamCount = propertyService.getIntProperty("stroom.benchmark.streamCount", 0);
        this.recordCount = propertyService.getIntProperty("stroom.benchmark.recordCount", 0);
        this.concurrentWriters = propertyService.getIntProperty("stroom.benchmark.concurrentWriters", 0);
    }

    BenchmarkClusterConfig() {
        this.streamCount = 0;
        this.recordCount = 0;
        this.concurrentWriters = 0;
    }

    int getStreamCount() {
        return streamCount;
    }

    int getRecordCount() {
        return recordCount;
    }

    int getConcurrentWriters() {
        return concurrentWriters;
    }
}
