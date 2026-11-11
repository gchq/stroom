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

package stroom.externaldatasource.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentStore;
import stroom.externaldatasource.shared.ExternalDataSourceDoc;

import java.util.List;

/// The store of [ExternalDataSourceDoc] documents.
public interface ExternalDataSourceStore extends DocumentStore<ExternalDataSourceDoc> {

    /// @return References to every external data source document.
    List<DocRef> list();
}
