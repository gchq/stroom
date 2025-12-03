/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
            if (analyticNotificationConfig != null) {
                if (!analyticNotificationConfig.isEnabled()) {
                    enabled = false;
                } else if (analyticNotificationConfig.isLimitNotifications() &&
                           count >= analyticNotificationConfig.getMaxNotifications()) {
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
