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

package stroom.processor.impl.db;

import event.logging.BaseAdvancedQueryItem;
import stroom.entity.CriteriaLoggingUtil;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.entity.SystemEntityServiceImpl;
import stroom.entity.shared.BaseResultList;
import stroom.entity.util.HqlBuilder;
import stroom.processor.StreamProcessorService;
import stroom.processor.shared.FindStreamProcessorCriteria;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.streamtask.shared.FindStreamProcessorCriteria;
import stroom.processor.shared.Processor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class StreamProcessorServiceImpl implements StreamProcessorService {

    private final Security security;

    @Inject
    StreamProcessorServiceImpl(final StroomEntityManager entityManager,
                               final Security security) {
        this.security = security;
    }

    @Override
    public Processor fetchInsecure(final int id) {
        return security.insecureResult(() -> fetch(id));
    }

    @Override
    public Processor create() {
        return null;
    }

    @Override
    public Processor update(final Processor processor) {
        return null;
    }

    @Override
    public int delete(final int id) {
        return 0;
    }

    @Override
    public Processor fetch(final int id) {
        return null;
    }

    @Override
    public BaseResultList<Processor> find(final FindStreamProcessorCriteria criteria) {
        return null;
    }

    @Override
    public Class<Processor> getEntityClass() {
        return Processor.class;
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

    private static class StreamProcessorQueryAppender extends QueryAppender<Processor, FindStreamProcessorCriteria> {
        StreamProcessorQueryAppender(StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        protected void appendBasicCriteria(final HqlBuilder sql, final String entityName,
                                           final FindStreamProcessorCriteria criteria) {
            super.appendBasicCriteria(sql, entityName, criteria);
            sql.appendDocRefSetQuery(entityName + ".pipelineUuid", criteria.getPipelineSet());
        }
    }
}
