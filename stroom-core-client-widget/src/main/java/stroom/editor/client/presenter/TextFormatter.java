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

package stroom.editor.client.presenter;

import java.util.function.Consumer;

/// Formats the text of an editor, replacing the editor's built-in formatting for its mode.
/// Formatting may be asynchronous, e.g. if it requires a call to the server.
@FunctionalInterface
public interface TextFormatter {

    /// Formats the supplied text.
    ///
    /// @param text                   The text to format.
    /// @param formattedTextConsumer  To be called with the formatted text once formatting is
    ///                               complete. It does not need to be called if formatting fails.
    void format(String text, Consumer<String> formattedTextConsumer);
}
