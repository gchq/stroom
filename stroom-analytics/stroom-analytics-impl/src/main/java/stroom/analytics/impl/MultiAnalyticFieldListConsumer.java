package stroom.analytics.impl;

import stroom.search.extraction.AnalyticFieldListConsumer;
import stroom.search.extraction.FieldValue;

import java.util.List;

public class MultiAnalyticFieldListConsumer implements AnalyticFieldListConsumer {

    private final List<AnalyticFieldListConsumer> consumers;

    public MultiAnalyticFieldListConsumer(final List<AnalyticFieldListConsumer> consumers) {
        this.consumers = consumers;
    }

    @Override
    public void accept(final List<FieldValue> fieldValues) {
        for (final AnalyticFieldListConsumer consumer : consumers) {
            consumer.accept(fieldValues);
        }
    }

    @Override
    public void start() {
        for (final AnalyticFieldListConsumer consumer : consumers) {
            consumer.start();
        }
    }

    @Override
    public void end() {
        for (final AnalyticFieldListConsumer consumer : consumers) {
            consumer.end();
        }
    }
}
