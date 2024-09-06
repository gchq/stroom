package stroom.analytics.api;

public interface NotificationState {

    boolean incrementAndCheckEnabled();

    void enableIfPossible();
}
