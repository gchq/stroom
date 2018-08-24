package stroom.kafka.pipeline;

public interface KafkaRecordMetaData {
    long getOffset();

    long getTimestamp();

    int getPartition();

    String getTopic();
}
