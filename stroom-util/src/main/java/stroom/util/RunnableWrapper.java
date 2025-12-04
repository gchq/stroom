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

package stroom.util;

import java.util.Objects;

/**
 * Wraps a {@link Runnable} with the purpose that the wrapped {@link Runnable}
 * can be lazily injected by Guice.
 */
public abstract class RunnableWrapper implements Runnable {

    private final Runnable runnable;

    public RunnableWrapper(final Runnable runnable) {
        this.runnable = Objects.requireNonNull(runnable);
    }

    @Override
    public void run() {
        runnable.run();
    }
}
