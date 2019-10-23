package stroom.connectors.kafka;

import org.apache.kafka.clients.producer.RecordMetadata;

public class WrappedRecordMetaData implements StroomKafkaRecordMetaData {

    private final RecordMetadata delegate;

    private WrappedRecordMetaData(final RecordMetadata delegate) {
        this.delegate = delegate;
    }

    public static StroomKafkaRecordMetaData wrap(final RecordMetadata recordMetadata) {
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
