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

package stroom.script.server;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.server.ObjectMarshaller;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.util.SQLBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.query.api.DocRef;
import stroom.entity.shared.DocRefs;
import stroom.script.shared.FindScriptCriteria;
import stroom.script.shared.Script;
import stroom.script.shared.ScriptService;
import stroom.security.SecurityContext;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;

@Component("scriptService")
@Transactional
public class ScriptServiceImpl extends DocumentEntityServiceImpl<Script, FindScriptCriteria> implements ScriptService {
    @Inject
    ScriptServiceImpl(final StroomEntityManager entityManager, final SecurityContext securityContext) {
        super(entityManager, securityContext);
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
    public Script copy(final Script entity, final DocRef folder, final String name) {
        // Load resources or dependencies if we don't have them. This can happen
        // as they are loaded lazily by the UI and so won't always be available
        // on the entity being saved.
        if (entity.isPersistent() && (entity.getResource() == null || entity.getResource().getData() == null
                || entity.getDependencies() == null)) {
            final Set<String> fetchSet = new HashSet<>();
            fetchSet.add(Script.FETCH_RESOURCE);
            final Script loaded = load(entity, fetchSet);

            if (entity.getResource() == null || entity.getResource().getData() == null) {
                entity.setResource(loaded.getResource());
            }
            if (entity.getDependencies() == null) {
                entity.setDependencies(loaded.getDependencies());
            }
        }

        return super.copy(entity, folder, name);
    }

    @Override
    protected QueryAppender<Script, FindScriptCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new ScriptQueryAppender(entityManager);
    }

    private static class ScriptQueryAppender extends QueryAppender<Script, FindScriptCriteria> {
        private final ObjectMarshaller<DocRefs> docRefSetMarshaller;

        public ScriptQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
            docRefSetMarshaller = new ObjectMarshaller<>(DocRefs.class);
        }

        @Override
        protected void appendBasicJoin(final SQLBuilder sql, final String alias, final Set<String> fetchSet) {
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
