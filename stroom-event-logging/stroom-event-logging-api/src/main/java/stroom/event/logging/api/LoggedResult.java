package stroom.event.logging.api;

import event.logging.EventAction;

import java.util.Objects;

public class LoggedResult<T_RESULT, T_EVENT_ACTION extends EventAction> {
    final T_RESULT result;
    final T_EVENT_ACTION eventAction;

    private LoggedResult(final T_RESULT result, final T_EVENT_ACTION eventAction) {
        this.result = result;
        this.eventAction = Objects.requireNonNull(eventAction);
    }

    public static <T_RESULT, T_EVENT_ACTION extends EventAction> LoggedResult<T_RESULT, T_EVENT_ACTION> of(
            final T_RESULT result,
            final T_EVENT_ACTION eventAction) {

        return new LoggedResult<>(result, eventAction);
    }

    public T_RESULT getResult() {
        return result;
    }

    public T_EVENT_ACTION getEventAction() {
        return eventAction;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final LoggedResult<?, ?> that = (LoggedResult<?, ?>) o;
        return Objects.equals(result, that.result) && Objects.equals(eventAction, that.eventAction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result, eventAction);
    }

    @Override
    public String toString() {
        return "LoggedResult{" +
                "result=" + result +
                ", eventAction=" + eventAction +
                '}';
    }
}
