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

package stroom.streamtask;

import event.logging.BaseAdvancedQueryItem;
import stroom.entity.CriteriaLoggingUtil;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.entity.SystemEntityServiceImpl;
import stroom.entity.util.HqlBuilder;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.streamtask.shared.FindStreamProcessorCriteria;
import stroom.streamtask.shared.StreamProcessor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Set;

@Singleton
public class StreamProcessorServiceImpl extends SystemEntityServiceImpl<StreamProcessor, FindStreamProcessorCriteria>
        implements StreamProcessorService {
    private final Security security;

    @Inject
    StreamProcessorServiceImpl(final StroomEntityManager entityManager,
                               final Security security) {
        super(entityManager, security);
        this.security = security;
    }

    @Override
    public StreamProcessor loadByIdInsecure(final long id) {
        return security.insecureResult(() -> loadById(id));
    }

    @Override
    public Class<StreamProcessor> getEntityClass() {
        return StreamProcessor.class;
    }

    @Override
    public FindStreamProcessorCriteria createCriteria() {
        return new FindStreamProcessorCriteria();
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindStreamProcessorCriteria criteria) {
        CriteriaLoggingUtil.appendCriteriaSet(items, "pipelineSet", criteria.getPipelineSet());
        super.appendCriteria(items, criteria);
    }

    @Override
    public StreamProcessorQueryAppender createQueryAppender(final StroomEntityManager entityManager) {
        return new StreamProcessorQueryAppender(entityManager);
    }

    @Override
    protected String permission() {
        return PermissionNames.MANAGE_PROCESSORS_PERMISSION;
    }

    private static class StreamProcessorQueryAppender extends QueryAppender<StreamProcessor, FindStreamProcessorCriteria> {
        StreamProcessorQueryAppender(StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        protected void appendBasicJoin(final HqlBuilder sql, final String alias, final Set<String> fetchSet) {
            super.appendBasicJoin(sql, alias, fetchSet);
            if (fetchSet != null && fetchSet.contains(PipelineDoc.DOCUMENT_TYPE)) {
                sql.append(" LEFT OUTER JOIN FETCH ");
                sql.append(alias);
                sql.append(".pipeline");
            }
        }

        @Override
        protected void appendBasicCriteria(final HqlBuilder sql, final String entityName,
                                           final FindStreamProcessorCriteria criteria) {
            super.appendBasicCriteria(sql, entityName, criteria);
            sql.appendDocRefSetQuery(entityName + ".pipelineUuid", criteria.getPipelineSet());
        }
    }
}
