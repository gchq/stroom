package stroom.statistics.internal;

import stroom.docref.DocRef;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class InternalStatisticsConfig extends HashMap<String, List<DocRef>> {
    public InternalStatisticsConfig() {
        put("benchmarkCluster", Arrays.asList(
                new DocRef("StatisticStore", "946a88c6-a59a-11e6-bdc4-0242ac110002", "Benchmark-Cluster Test"),
                new DocRef("StroomStatsStore", "2503f703-5ce0-4432-b9d4-e3272178f47e", "Benchmark-Cluster Test")));

        put("pipelineStreamProcessor", Arrays.asList(
                new DocRef("StatisticStore", "946a80fc-a59a-11e6-bdc4-0242ac110002", "PipelineStreamProcessor"),
                new DocRef("StroomStatsStore", "efd9bad4-0bab-460f-ae98-79e9717deeaf", "PipelineStreamProcessor")));

        put("metaDataStreamSize", Arrays.asList(
                new DocRef("StatisticStore", "946a8814-a59a-11e6-bdc4-0242ac110002", "Meta Data-Stream Size"),
                new DocRef("StroomStatsStore", "3b25d63b-5472-44d0-80e8-8eea94f40f14", "Meta Data-Stream Size")));

        put("eventsPerSecond", Arrays.asList(
                new DocRef("StatisticStore", "a9936548-2572-448b-9d5b-8543052c4d92", "EPS"),
                new DocRef("StroomStatsStore", "cde67df0-0f77-45d3-b2c0-ee8bb7b3c9c6", "EPS")));

        put("cpu", Arrays.asList(
                new DocRef("StatisticStore", "af08c4a7-ee7c-44e4-8f5e-e9c6be280434", "CPU"),
                new DocRef("StroomStatsStore", "1edfd582-5e60-413a-b91c-151bd544da47", "CPU")));

        put("metaDataStreamsReceived", Arrays.asList(
                new DocRef("StatisticStore", "946a87bc-a59a-11e6-bdc4-0242ac110002", "Meta Data-Streams Received"),
                new DocRef("StroomStatsStore", "5535f493-29ae-4ee6-bba6-735aa3104136", "Meta Data-Streams Received")));

        put("streamTaskQueueSize", Arrays.asList(
                new DocRef("StatisticStore", "946a7f0f-a59a-11e6-bdc4-0242ac110002", "Stream Task Queue Size"),
                new DocRef("StroomStatsStore", "4ce8d6e7-94be-40e1-8294-bf29dd089962", "Stream Task Queue Size")));

        put("volumes", Arrays.asList(
                new DocRef("StatisticStore", "ac4d8d10-6f75-4946-9708-18b8cb42a5a3", "Volumes"),
                new DocRef("StroomStatsStore", "60f4f5f0-4cc3-42d6-8fe7-21a7cec30f8e", "Volumes")));

        put("memory", Arrays.asList(
                new DocRef("StatisticStore", "77c09ccb-e251-4ca5-bca0-56a842654397", "Memory"),
                new DocRef("StroomStatsStore", "d8a7da4f-ef6d-47e0-b16a-af26367a2798", "Memory")));

        put("heapHistogramInstances", Arrays.asList(
                new DocRef("StatisticStore", "e4f243b8-2c70-4d6e-9d5a-16466bf8764f", "Heap Histogram Instances"),
                new DocRef("StroomStatsStore", "bdd933a4-4309-47fd-98f6-1bc2eb555f20", "Heap Histogram Instances")));

        put("heapHistogramBytes", Arrays.asList(
                new DocRef("StatisticStore", "934a1600-b456-49bf-9aea-f1e84025febd", "Heap Histogram Bytes"),
                new DocRef("StroomStatsStore", "b0110ab4-ac25-4b73-b4f6-96f2b50b456a", "Heap Histogram Bytes")));
    }
}
