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

import stroom.index.shared.IndexException;

/// Thrown when a run of a processor task must add no more documents to the index, e.g. as its task has been
/// taken from it, or documents it added have been lost. How the run is to end has been said already.
public class RunStoppedException extends IndexException {

    /// Creates the exception.
    ///
    /// @param message Why the run was stopped.
    public RunStoppedException(final String message) {
        super(message);
    }
}
