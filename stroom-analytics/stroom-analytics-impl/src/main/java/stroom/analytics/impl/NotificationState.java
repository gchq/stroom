package stroom.analytics.impl;

public interface NotificationState {
    NotificationState NO_OP = new NotificationStateNoOp();

    boolean incrementAndCheckEnabled();

    boolean isEnabled();

    void enableIfPossible();
}
