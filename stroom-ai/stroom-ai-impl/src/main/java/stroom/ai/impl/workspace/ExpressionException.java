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

/// A mistake in an expression (e.g. a function given the wrong arguments, or a column the table doesn't have),
/// which the model is to fix from the message, rather than a problem with the table: so it doesn't count towards
/// the table tool falling back to summarising a column.
public class ExpressionException extends WorkspaceException {

    /// @param message What is wrong, and what to do about it, for the model.
    public ExpressionException(final String message) {
        super(message);
    }
}
