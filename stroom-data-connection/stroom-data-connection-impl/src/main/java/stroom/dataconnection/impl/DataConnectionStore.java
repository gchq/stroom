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

package stroom.dataconnection.impl;

import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentStore;

import java.util.List;

/// The store of [DataConnectionDoc] documents.
public interface DataConnectionStore extends DocumentStore<DataConnectionDoc> {

    /// @return References to every Data Connection document.
    List<DocRef> list();

    /// Gives a document the workbook its Excel settings name: a new upload, the workbook of the
    /// document being copied, or else the one already stored. The upload key and the document to copy
    /// from are cleared. Settings with no file name and no new workbook have none.
    ///
    /// @param document A document as the editor sends it, without its workbook.
    /// @return The document with its workbook, if it has one.
    /// @throws stroom.util.shared.EntityServiceException If the upload or the document to copy from
    ///                                                   cannot be found.
    DataConnectionDoc withWorkbook(DataConnectionDoc document);
}
