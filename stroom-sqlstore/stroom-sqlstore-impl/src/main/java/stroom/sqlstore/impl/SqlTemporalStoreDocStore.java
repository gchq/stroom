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

package stroom.sqlstore.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandler;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;

import java.util.List;

public interface SqlTemporalStoreDocStore extends
        ExplorerActionHandler,
        DocumentActionHandler<SqlTemporalStoreDoc>,
        ImportExportActionHandler,
        ContentIndexable {

    List<DocRef> list();
}
