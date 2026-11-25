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

import java.util.function.Consumer;

/// What is saved once a document is, before it is read again, e.g. its assets.
///
/// @param <D> The document's type.
public interface PostSave<D> {

    /// Saves it.
    ///
    /// @param saved  The document, as saved.
    /// @param done   Given the document once it is saved.
    /// @param failed Run instead if it can't be, once the user has been told.
    void run(D saved, Consumer<D> done, Runnable failed);
}
