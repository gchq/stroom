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

package stroom.document.client.editor;

import stroom.util.shared.NullSafe;

/// Writes a document's text that isn't set as it was read: it is stored as none or as blank, and shown
/// as blank, so writing the blank back would change the document when nothing was typed.
public final class UnsetText {

    private UnsetText() {
        // Static utility
    }

    /// @param shown The text shown.
    /// @param read  The text read, or null.
    /// @return The text shown, or that read if both are blank.
    public static String asRead(final String shown, final String read) {
        return NullSafe.isBlankString(shown) && NullSafe.isBlankString(read)
                ? read
                : shown;
    }
}
