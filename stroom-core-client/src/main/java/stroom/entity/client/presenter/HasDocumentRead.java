/*
 * Copyright 2016 Crown Copyright
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

package stroom.entity.client.presenter;

import stroom.docref.DocRef;

/// Something that shows a document, or a part of one. Whether it may be changed isn't an argument:
/// a part watches [stroom.widget.component.client.Contexts#READ_ONLY], which its document provides.
///
/// @param <E> The type of the document.
public interface HasDocumentRead<E> {

    /// Shows the document.
    ///
    /// @param docRef   The document's reference.
    /// @param document The document.
    void read(DocRef docRef, E document);
}
