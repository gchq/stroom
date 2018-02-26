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

package stroom.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import stroom.explorer.ExplorerActionHandler;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.ImportExportActionHandler;
import stroom.entity.DocumentEntityServiceImpl;
import stroom.entity.ObjectMarshaller;
import stroom.entity.QueryAppender;
import stroom.entity.util.HqlBuilder;
import stroom.entity.StroomEntityManager;
import stroom.entity.shared.DocRefs;
import stroom.importexport.ImportExportHelper;
import stroom.query.api.v2.DocRef;
import stroom.script.shared.FindScriptCriteria;
import stroom.script.shared.Script;
import stroom.security.SecurityContext;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Transactional
public class ScriptServiceImpl extends DocumentEntityServiceImpl<Script, FindScriptCriteria> implements ScriptService, ExplorerActionHandler, ImportExportActionHandler {
    public static final Set<String> FETCH_SET = Collections.singleton(Script.FETCH_RESOURCE);

    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptServiceImpl.class);

    @Inject
    ScriptServiceImpl(final StroomEntityManager entityManager,
                      final ImportExportHelper importExportHelper,
                      final SecurityContext securityContext) {
        super(entityManager, importExportHelper, securityContext);
    }

    @Override
    public Class<Script> getEntityClass() {
        return Script.class;
    }

    @Override
    public FindScriptCriteria createCriteria() {
        return new FindScriptCriteria();
    }

    @Override
    public Script loadByUuidInsecure(final String uuid, final Set<String> fetchSet) {
        return super.loadByUuidInsecure(uuid, fetchSet);
    }

    //    @Override
//    public DocRef copy(final String uuid, final String parentFolderUUID) {
//        final Set<String> fetchSet = new HashSet<>();
//        fetchSet.add(Script.FETCH_RESOURCE);
//
//        final Script entity = loadByUuid(uuid, fetchSet);
//
//        // This is going to be a copy so clear the persistence so save will create a new DB entry.
//        entity.clearPersistence();
//
//        setFolder(entity, parentFolderUUID);
//
//        final Script result = create(entity);
//        return DocRefUtil.create(result);
//    }
//
//    @Override
//    public Object read(final DocRef docRef) {
//        return loadByUuid(docRef.getUuid(), FETCH_SET);
//    }


    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        final Set<DocRef> docs = super.listDocuments();
        return docs.stream().collect(Collectors.toMap(Function.identity(), this::getDependencies));
    }

    private Set<DocRef> getDependencies(final DocRef docRef) {
        try {
            final Script script = loadByUuid(docRef.getUuid());
            return new HashSet<>(script.getDependencies().getDoc());
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return Collections.emptySet();
    }

    @Override
    protected QueryAppender<Script, FindScriptCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new ScriptQueryAppender(entityManager);
    }

    @Override
    public DocumentType getDocumentType() {
        return new DocumentType(99, Script.ENTITY_TYPE, Script.ENTITY_TYPE);
    }

    private static class ScriptQueryAppender extends QueryAppender<Script, FindScriptCriteria> {
        private final ObjectMarshaller<DocRefs> docRefSetMarshaller;

        ScriptQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
            docRefSetMarshaller = new ObjectMarshaller<>(DocRefs.class);
        }

        @Override
        protected void appendBasicJoin(final HqlBuilder sql, final String alias, final Set<String> fetchSet) {
            super.appendBasicJoin(sql, alias, fetchSet);
            if (fetchSet != null) {
                if (fetchSet.contains("all") || fetchSet.contains(Script.FETCH_RESOURCE)) {
                    sql.append(" LEFT OUTER JOIN FETCH " + alias + ".resource");
                }
            }
        }

        @Override
        protected void preSave(final Script entity) {
            super.preSave(entity);
            entity.setDependenciesXML(docRefSetMarshaller.marshal(entity.getDependencies()));
        }

        @Override
        protected void postLoad(final Script entity) {
            entity.setDependencies(docRefSetMarshaller.unmarshal(entity.getDependenciesXML()));
            super.postLoad(entity);
        }
    }
}
