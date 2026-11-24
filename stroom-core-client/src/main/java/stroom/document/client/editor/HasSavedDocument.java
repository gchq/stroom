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

/// A tab's content that keeps the document it was given to read, e.g. to ask the server about it. It is
/// told of the document saved when that isn't read into it, as the tabs were changed meanwhile
/// ([TabbedController#keepChanges]).
///
/// @param <D> The document's type.
public interface HasSavedDocument<D> {

    /// Takes the document saved in place of the one read, leaving what is shown as it is.
    ///
    /// @param saved The document, as saved.
    void setSavedDocument(D saved);
}
