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

package stroom.query.language.functions.ref;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/// Passes on the warnings that functions give as they work, such as that a limit has been reached.
///
/// A function can reach the same limit for a great many rows, so each message is only passed on once.
///
/// A result store sets what is done with the warnings, and the functions compiled from the same
/// [ValueReferenceIndex] give them. Until it is set they are dropped.
public final class FunctionWarnings {

    private final Set<String> given = ConcurrentHashMap.newKeySet();
    private volatile Consumer<String> consumer;

    /// Sets what is done with the warnings.
    ///
    /// @param consumer Given each different message once, on the thread that the function is used by.
    public void setConsumer(final Consumer<String> consumer) {
        this.consumer = consumer;
    }

    /// Gives a warning, unless the same one has already been given.
    ///
    /// @param message The message.
    public void warn(final String message) {
        final Consumer<String> current = consumer;
        if (current != null && given.add(message)) {
            current.accept(message);
        }
    }
}
