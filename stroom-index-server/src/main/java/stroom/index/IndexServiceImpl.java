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
 */

package stroom.index;


import stroom.entity.DocumentEntityServiceImpl;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.explorer.ExplorerActionHandler;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.ImportExportActionHandler;
import stroom.importexport.ImportExportHelper;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.security.SecurityContext;
import stroom.persist.EntityManagerSupport;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
// @Transactional
public class IndexServiceImpl extends DocumentEntityServiceImpl<Index, FindIndexCriteria> implements IndexService, ExplorerActionHandler, ImportExportActionHandler {
    @Inject
    IndexServiceImpl(final StroomEntityManager entityManager,
                     final EntityManagerSupport entityManagerSupport,
                     final ImportExportHelper importExportHelper,
                     final SecurityContext securityContext) {
        super(entityManager, entityManagerSupport, importExportHelper, securityContext);
    }

    @Override
    public Class<Index> getEntityClass() {
        return Index.class;
    }

    @Override
    public FindIndexCriteria createCriteria() {
        return new FindIndexCriteria();
    }

    @Override
    protected QueryAppender<Index, FindIndexCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new IndexQueryAppender(entityManager);
    }

    @Override
    public DocumentType getDocumentType() {
        return new DocumentType(10, Index.ENTITY_TYPE, Index.ENTITY_TYPE);
    }

    private static class IndexQueryAppender extends QueryAppender<Index, FindIndexCriteria> {
        private final IndexMarshaller marshaller;

        IndexQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
            marshaller = new IndexMarshaller();
        }

        @Override
        protected void preSave(final Index entity) {
            super.preSave(entity);
            marshaller.marshal(entity);
        }

        @Override
        protected void postLoad(final Index entity) {
            marshaller.unmarshal(entity);
            super.postLoad(entity);
        }
    }
}
