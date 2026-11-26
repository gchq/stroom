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

package stroom.processor.api;

/// Thrown by a run of a processor task that could not write its error stream, so must not complete, as nothing would
/// say what errors it had. This is almost always because of where the stream is written, e.g. a full volume, rather
/// than what is being processed, so it isn't counted as a fault of the task.
public class ErrorStreamException extends RuntimeException {

    /// Creates the exception.
    ///
    /// @param message What happened.
    /// @param cause   Why the error stream could not be written.
    public ErrorStreamException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
