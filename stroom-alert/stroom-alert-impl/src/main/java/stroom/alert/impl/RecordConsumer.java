package stroom.alert.impl;

import stroom.alert.impl.RecordConsumer.Record;

import java.util.List;
import java.util.function.Consumer;

public interface RecordConsumer extends Consumer<Record> {

    record Data(String name, String value) {

    }

    record Record(List<Data> list) {

    }
}
