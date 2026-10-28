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

package stroom.entity.client.component;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/// Whether a document being edited has changes that aren't saved: the rule [DocumentEditor] and
/// [DocumentEditorHost] work their dirty state out by, once the user changes something.
final class DocumentChanges {

    private DocumentChanges() {
        // Static utility
    }

    /// Whether what the tabs show differs from the document read, or something saved with the
    /// document (e.g. its assets) has changes. The latter is asked first, as writing the document
    /// out, on every key press in an editor, may cost more.
    ///
    /// @param read            The document as read.
    /// @param written         Writes what the tabs show into a copy of it.
    /// @param associatedDirty Whether what is saved with it has changes.
    /// @param <D>             The document's type, which compares by value.
    /// @return Whether there are changes to save.
    static <D> boolean hasChanges(final D read,
                                  final Supplier<D> written,
                                  final BooleanSupplier associatedDirty) {
        return associatedDirty.getAsBoolean() || !Objects.equals(read, written.get());
    }
}
