package stroom.analytics.impl;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.List;

class MultiValuesConsumer implements ValuesConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MultiValuesConsumer.class);
    private final List<ValuesConsumer> subConsumers;

    MultiValuesConsumer(final List<ValuesConsumer> subConsumers) {
        this.subConsumers = subConsumers;
    }

    @Override
    public void add(final Val[] values) {
        if (values != null && values.length > 0) {
            subConsumers.forEach(subReceiver -> {
                try {
                    subReceiver.add(values);
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            });
        }
    }
}
