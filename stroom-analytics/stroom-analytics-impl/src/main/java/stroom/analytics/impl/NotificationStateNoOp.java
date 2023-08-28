package stroom.analytics.impl;

public class NotificationStateNoOp implements NotificationState {

    @Override
    public boolean incrementAndCheckEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void enableIfPossible() {
    }
}
