/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.rest;

import com.google.gwt.user.client.Timer;

/// A [ReplyScheduler] that uses a GWT [Timer] for each reply.
public final class TimerReplyScheduler implements ReplyScheduler {

    /// Schedules the task on a new [Timer].
    ///
    /// @param delayMillis How long to wait.
    /// @param task        The task.
    /// @return A handle that cancels the timer.
    @Override
    public Scheduled schedule(final int delayMillis, final Runnable task) {
        final Timer timer = new Timer() {
            @Override
            public void run() {
                task.run();
            }
        };
        timer.schedule(Math.max(1, delayMillis));
        return timer::cancel;
    }
}
