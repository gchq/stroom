package stroom.kafka;

import java.util.function.Consumer;

public interface StroomKafkaProducer {

    void send(final StroomKafkaProducerRecord<String, String> stroomRecord,
              final FlushMode flushMode,
              final Consumer<Exception> exceptionHandler);
    void flush();
}
