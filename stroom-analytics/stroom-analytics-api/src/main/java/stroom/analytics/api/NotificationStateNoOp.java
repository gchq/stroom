package stroom.analytics.api;

public class NotificationStateNoOp implements NotificationState {

    @Override
    public boolean incrementAndCheckEnabled() {
        return true;
    }

    @Override
    public void enableIfPossible() {
    }
}
