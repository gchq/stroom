package stroom.analytics.api;

public interface NotificationState {
    NotificationState NO_OP = new NotificationStateNoOp();

    boolean incrementAndCheckEnabled();

    boolean isEnabled();

    void enableIfPossible();
}
