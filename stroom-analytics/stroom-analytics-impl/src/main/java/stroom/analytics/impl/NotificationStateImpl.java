package stroom.analytics.impl;

import stroom.analytics.api.NotificationState;
import stroom.analytics.shared.NotificationConfig;
import stroom.util.time.SimpleDurationUtil;

import java.time.Instant;

public class NotificationStateImpl implements NotificationState {

    private NotificationConfig analyticNotificationConfig;
    private long count;
    private boolean enabled;
    private Instant lastNotificationTimeMs;

    public synchronized void update(final NotificationConfig analyticNotificationConfig) {
        this.analyticNotificationConfig = analyticNotificationConfig;
    }

    @Override
    public synchronized boolean incrementAndCheckEnabled() {
        if (enabled) {
            if (analyticNotificationConfig != null && analyticNotificationConfig.isLimitNotifications()) {
                if (count >= analyticNotificationConfig.getMaxNotifications()) {
                    enabled = false;
                }
            }

            if (enabled) {
                count++;
                lastNotificationTimeMs = Instant.now();
            }
        }
        return enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public synchronized void enableIfPossible() {
        // Determine if notifications have been disabled.
        if (!enabled && analyticNotificationConfig != null) {
            if (lastNotificationTimeMs == null) {
                reset();

            } else {
                // See if we should resume notifications.
                if (analyticNotificationConfig.getResumeAfter() != null) {
                    final Instant resumeTime = SimpleDurationUtil.plus(
                            lastNotificationTimeMs,
                            analyticNotificationConfig.getResumeAfter());
                    final Instant now = Instant.now();
                    if (now.isAfter(resumeTime)) {
                        reset();
                    }
                }
            }
        }
    }

    private void reset() {
        count = 0;
        enabled = true;
        lastNotificationTimeMs = null;
    }

    @Override
    public String toString() {
        return "NotificationState{" +
                "analyticNotificationConfig=" + analyticNotificationConfig +
                ", count=" + count +
                ", enabled=" + enabled +
                ", lastNotificationTimeMs=" + lastNotificationTimeMs +
                '}';
    }
}
