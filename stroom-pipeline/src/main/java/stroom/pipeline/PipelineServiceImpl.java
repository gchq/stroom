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

package stroom.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.inject.persist.Transactional;
import stroom.entity.DocumentEntityServiceImpl;
import stroom.entity.ObjectMarshaller;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.explorer.ExplorerActionHandler;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.ImportExportActionHandler;
import stroom.importexport.ImportExportHelper;
import stroom.pipeline.shared.FindPipelineEntityCriteria;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.query.api.v2.DocRef;
import stroom.security.SecurityContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
@Transactional
public class PipelineServiceImpl extends DocumentEntityServiceImpl<PipelineEntity, FindPipelineEntityCriteria>
        implements PipelineService, ExplorerActionHandler, ImportExportActionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineServiceImpl.class);

    @Inject
    PipelineServiceImpl(final StroomEntityManager entityManager,
                        final ImportExportHelper importExportHelper,
                        final SecurityContext securityContext) {
        super(entityManager, importExportHelper, securityContext);
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

    @Transactional
    @Override
    public PipelineEntity loadByUuidWithoutUnmarshal(final String uuid) {
        return loadByUuid(uuid, Collections.emptySet(), null);
    }

    @Override
    public DocRef copyDocument(final String originalUuid,
                               final String copyUuid,
                               final Map<String, String> otherCopiesByOriginalUuid,
                               final String parentFolderUUID) {
        final DocRef copiedDocRef = super.copyDocument(originalUuid,
                copyUuid,
                otherCopiesByOriginalUuid,
                parentFolderUUID);

        return makeCopyUuidReplacements(copiedDocRef,
                otherCopiesByOriginalUuid,
                PipelineEntity::getData,
                PipelineEntity::setData);
    }

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        final Set<DocRef> docs = super.listDocuments();
        return docs.stream().collect(Collectors.toMap(Function.identity(), this::getDependencies));
    }

    private Set<DocRef> getDependencies(final DocRef docRef) {
        final Set<DocRef> docRefs = Collections.newSetFromMap(new ConcurrentHashMap<>());

        try {
            final PipelineEntity pipelineEntity = loadByUuid(docRef.getUuid());

            if (pipelineEntity.getParentPipeline() != null) {
                docRefs.add(pipelineEntity.getParentPipeline());
            }

            if (pipelineEntity.getPipelineData() != null) {
                if (pipelineEntity.getPipelineData().getProperties() != null &&
                        pipelineEntity.getPipelineData().getProperties().getAdd() != null) {
                    final List<PipelineProperty> pipelineProperties = pipelineEntity.getPipelineData().getProperties().getAdd();
                    pipelineProperties.forEach(prop -> {
                        if (prop.getValue() != null && prop.getValue().getEntity() != null) {
                            docRefs.add(prop.getValue().getEntity());
                        }
                    });
                }

                if (pipelineEntity.getPipelineData().getPipelineReferences() != null &&
                        pipelineEntity.getPipelineData().getPipelineReferences().getAdd() != null) {
                    final List<PipelineReference> pipelineReferences = pipelineEntity.getPipelineData().getPipelineReferences().getAdd();
                    pipelineReferences.forEach(ref -> {
                        if (ref.getFeed() != null) {
                            docRefs.add(ref.getFeed());
                        }

                        if (ref.getPipeline() != null) {
                            docRefs.add(ref.getPipeline());
                        }
                    });
                }
            }


        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return docRefs;
    }

    @Override
    protected QueryAppender<PipelineEntity, FindPipelineEntityCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new PipelineEntityQueryAppender(entityManager);
    }

    @Override
    public DocumentType getDocumentType() {
        return new DocumentType(6, PipelineEntity.ENTITY_TYPE, PipelineEntity.ENTITY_TYPE);
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
