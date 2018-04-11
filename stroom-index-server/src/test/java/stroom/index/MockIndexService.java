/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.index;

import stroom.entity.MockDocumentEntityService;
import stroom.explorer.ExplorerActionHandler;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.ImportExportActionHandler;
import stroom.importexport.ImportExportHelper;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MockIndexService extends MockDocumentEntityService<Index, FindIndexCriteria> implements IndexService, ExplorerActionHandler, ImportExportActionHandler {
    @Inject
    public MockIndexService(final ImportExportHelper importExportHelper) {
        super(importExportHelper);
    }

    @Override
    public Class<Index> getEntityClass() {
        return Index.class;
    }

    @Override
    public DocumentType getDocumentType() {
        return new DocumentType(10, Index.ENTITY_TYPE, Index.ENTITY_TYPE);
    }
}
