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

package stroom.ai.impl.workspace;

/// A problem with a request to the chat workspace (e.g. a bad path, a missing file or a limit reached), described
/// in words the model can act on. Tools return the message to the model rather than failing.
public class WorkspaceException extends RuntimeException {

    /// @param message What is wrong, and what to do about it, for the model.
    public WorkspaceException(final String message) {
        super(message);
    }
}
