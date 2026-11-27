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

/// Thrown when a document is given to a writer whose close has begun. Nothing is wrong with the shard, which a
/// new writer can take the document for, so unlike an [stroom.index.shared.IndexException] it does not take
/// the shard out of use.
public class WriterClosingException extends RuntimeException {

    /// Creates the exception.
    ///
    /// @param message What happened.
    public WriterClosingException(final String message) {
        super(message);
    }
}
