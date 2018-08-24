package stroom.kafka.impl;

import stroom.docstore.JsonSerialiser2;
import stroom.kafka.shared.KafkaConfigDoc;

class KafkaConfigSerialiser extends JsonSerialiser2<KafkaConfigDoc> {
    KafkaConfigSerialiser() {
        super(KafkaConfigDoc.class);
    }
}