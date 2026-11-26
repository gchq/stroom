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

package stroom.index.impl;

/// Thrown when the record of which shards a run has written to could not be kept, e.g. as the database could
/// not be reached. No fault of the shard's or of the document's, so the shard stays in use and the fault is not
/// counted against the task.
public class IndexRunTrackingException extends RuntimeException {

    /// Creates the exception.
    ///
    /// @param message What happened.
    /// @param cause   Why.
    public IndexRunTrackingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
