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

/// Runs a [FixtureDispatcher]'s replies later, as a real request's response arrives later. In the
/// browser this is [TimerReplyScheduler]; JVM tests use a fake that runs the replies on demand.
public interface ReplyScheduler {

    /// Schedules a task.
    ///
    /// @param delayMillis How long to wait, at least 1ms so the reply is always asynchronous.
    /// @param task        The task.
    /// @return A handle to cancel the task with.
    Scheduled schedule(int delayMillis, Runnable task);

    // --------------------------------------------------------------------------------


    /// A scheduled task.
    interface Scheduled {

        /// Cancels the task, if it hasn't run yet.
        void cancel();
    }
}
