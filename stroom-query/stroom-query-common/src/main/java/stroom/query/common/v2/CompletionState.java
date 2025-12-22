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

package stroom.query.common.v2;

import java.util.concurrent.TimeUnit;

public interface CompletionState {

    /**
     * Call to notify the object or stage holding this completion state that the upstream process has completed and will
     * not provide any further data or that this stage is allowed to complete as no further data is required. Calling
     * this method may cause the object or stage to complete immediately or may just tell it that it should begin the
     * completion process which may involve some final consumption of queued data before actually completing,
     * i.e. `isComplete()` may not return true immediately after this method has been called.
     */
    void signalComplete();

    /**
     * After the object or stage holding this completion state has been signaled to complete via the `signalComplete()`
     * method it may perform any remaining processing before actually completing. Once all remaining work has been done
     * this method will return true.
     * <p>
     * Note that if the current thread is interrupted then this method may also return true as an interrupted thread can
     * also indicate completion of some stages.
     *
     * @return True if there is no remaining work to be done by this object or stage.
     */
    boolean isComplete();

    /**
     * Wait indefinitely for this completion state to complete or for the current thread to be interrupted.
     *
     * @throws InterruptedException If the current thread is interrupted.
     */
    void awaitCompletion() throws InterruptedException;

    /**
     * Wait for this completion state to complete or for the current thread to be interrupted. This method will wait for
     * completion until the specified timeout has elapsed. If the timeout period elapses before completion this method
     * will return false. As soon as the state is complete then this method will return true.
     *
     * @param timeout The timeout period.
     * @param unit    The timeout units.
     * @return If the timeout period elapses before completion this method will return false. As soon as the state is
     * complete then this method will return true.
     * @throws InterruptedException If the current thread is interrupted.
     */
    boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException;
}
