package stroom.analytics.impl;

import stroom.pipeline.filter.FieldValue;
import stroom.query.common.v2.StringFieldValue;

import java.util.List;

public class MultiAnalyticFieldListConsumer implements AnalyticFieldListConsumer {

    private final List<AnalyticFieldListConsumer> consumers;

    public MultiAnalyticFieldListConsumer(final List<AnalyticFieldListConsumer> consumers) {
        this.consumers = consumers;
    }

    @Override
    public void acceptFieldValues(final List<FieldValue> fieldValues) {
        for (final AnalyticFieldListConsumer consumer : consumers) {
            consumer.acceptFieldValues(fieldValues);
        }
    }

    @Override
    public void acceptStringValues(final List<StringFieldValue> stringValues) {
        for (final AnalyticFieldListConsumer consumer : consumers) {
            consumer.acceptStringValues(stringValues);
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
