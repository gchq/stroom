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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class TransferState {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TransferState.class);

    private final AtomicBoolean terminated = new AtomicBoolean();
    private volatile Thread thread;

    public boolean isTerminated() {
        return terminated.get();
    }

    public synchronized void terminate() {
        terminated.set(true);
        if (thread != null) {
            thread.interrupt();
        }
    }

    public synchronized void setThread(final Thread thread) {
        this.thread = thread;
        if (terminated.get()) {
            if (thread != null) {
                thread.interrupt();
            } else if (Thread.interrupted()) {
                LOGGER.debug(() -> "Cleared interrupt state");
            }
        }
    }
}
