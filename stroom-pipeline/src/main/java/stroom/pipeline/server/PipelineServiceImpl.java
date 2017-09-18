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

package stroom.pipeline.server;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.logging.DocumentEventLog;
import stroom.entity.server.AutoMarshal;
import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.server.ObjectMarshaller;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.util.StroomEntityManager;
import stroom.importexport.server.ImportExportHelper;
import stroom.pipeline.shared.FindPipelineEntityCriteria;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v1.DocRef;
import stroom.security.SecurityContext;

import javax.inject.Inject;
import java.util.Collections;

@Component("pipelineService")
@Transactional
@AutoMarshal
public class PipelineServiceImpl extends DocumentEntityServiceImpl<PipelineEntity, FindPipelineEntityCriteria>
        implements PipelineService {
    @Inject
    PipelineServiceImpl(final StroomEntityManager entityManager, final ImportExportHelper importExportHelper, final SecurityContext securityContext, final DocumentEventLog documentEventLog) {
        super(entityManager, importExportHelper, securityContext, documentEventLog);
    }

    @Override
    public Class<PipelineEntity> getEntityClass() {
        return PipelineEntity.class;
    }

    @Override
    public FindPipelineEntityCriteria createCriteria() {
        return new FindPipelineEntityCriteria();
    }

    @Override
    public PipelineEntity saveWithoutMarshal(final PipelineEntity pipelineEntity) {
        return save(pipelineEntity, null);
    }

    @Transactional(readOnly = true)
    @Override
    public PipelineEntity loadByIdWithoutUnmarshal(final long id) {
        return loadById(id, Collections.emptySet(), null);
    }

    @Override
    protected QueryAppender<PipelineEntity, FindPipelineEntityCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new PipelineEntityQueryAppender(entityManager);
    }

    private static class PipelineEntityQueryAppender extends QueryAppender<PipelineEntity, FindPipelineEntityCriteria> {
        private final ObjectMarshaller<DocRef> docRefMarshaller;
        private final PipelineMarshaller marshaller;

        PipelineEntityQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
            docRefMarshaller = new ObjectMarshaller<>(DocRef.class);
            marshaller = new PipelineMarshaller();
        }

        @Override
        protected void preSave(final PipelineEntity entity) {
            super.preSave(entity);
            entity.setParentPipelineXML(docRefMarshaller.marshal(entity.getParentPipeline()));
            marshaller.marshal(entity);
        }

        @Override
        protected void postLoad(final PipelineEntity entity) {
            entity.setParentPipeline(docRefMarshaller.unmarshal(entity.getParentPipelineXML()));
            marshaller.unmarshal(entity);
            super.postLoad(entity);
        }
    }
}
