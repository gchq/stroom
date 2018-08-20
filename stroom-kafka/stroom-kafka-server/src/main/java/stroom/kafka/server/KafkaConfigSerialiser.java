package stroom.kafka.server;

import stroom.docstore.JsonSerialiser2;
import stroom.kafka.shared.KafkaConfigDoc;

public class KafkaConfigSerialiser extends JsonSerialiser2<KafkaConfigDoc> {
    public KafkaConfigSerialiser() {
        super(KafkaConfigDoc.class);
    }
}