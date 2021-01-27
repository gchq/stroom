package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;

import java.util.function.Consumer;

public interface Receiver {
    Consumer<Val[]> getValuesConsumer();

    Consumer<Throwable> getErrorConsumer();

    Consumer<Long> getCompletionConsumer();
}
