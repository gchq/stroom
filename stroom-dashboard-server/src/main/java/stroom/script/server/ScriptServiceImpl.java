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

package stroom.script.server;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.logging.DocumentEventLog;
import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.server.ObjectMarshaller;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.DocRefs;
import stroom.importexport.server.ImportExportHelper;
import stroom.script.shared.FindScriptCriteria;
import stroom.script.shared.Script;
import stroom.security.SecurityContext;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;

@Component("scriptService")
@Transactional
public class ScriptServiceImpl extends DocumentEntityServiceImpl<Script, FindScriptCriteria> implements ScriptService {
    public static final Set<String> FETCH_SET = Collections.singleton(Script.FETCH_RESOURCE);

    @Inject
    ScriptServiceImpl(final StroomEntityManager entityManager, final ImportExportHelper importExportHelper, final SecurityContext securityContext, final DocumentEventLog documentEventLog) {
        super(entityManager, importExportHelper, securityContext, documentEventLog);
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
    protected QueryAppender<Script, FindScriptCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new ScriptQueryAppender(entityManager);
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
