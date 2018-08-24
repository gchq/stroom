package stroom.kafka.impl;

import org.apache.kafka.clients.producer.RecordMetadata;
import stroom.kafka.pipeline.KafkaRecordMetaData;

class WrappedRecordMetaData implements KafkaRecordMetaData {
    private final RecordMetadata delegate;

    private WrappedRecordMetaData(final RecordMetadata delegate) {
        this.delegate = delegate;
    }

    static KafkaRecordMetaData wrap(final RecordMetadata recordMetadata) {
        return new WrappedRecordMetaData(recordMetadata);
    }

    @Override
    public long getOffset() {
        return delegate.offset();
    }

    @Override
    public long getTimestamp() {
        return delegate.timestamp();
    }

    @Override
    public int getPartition() {
        return delegate.partition();
    }

    @Override
    public String getTopic() {
        return delegate.topic();
    }
}
