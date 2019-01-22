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

import stroom.entity.shared.BaseResultList;
import stroom.processor.StreamProcessorService;
import stroom.processor.impl.db.dao.ProcessorDao;
import stroom.processor.shared.FindStreamProcessorCriteria;
import stroom.processor.shared.Processor;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;

import javax.inject.Inject;

public class StreamProcessorServiceImpl implements StreamProcessorService {

    private static final String PERMISSION = PermissionNames.MANAGE_PROCESSORS_PERMISSION;
    private final Security security;
    private final ProcessorDao processorDao;

    @Inject
    StreamProcessorServiceImpl(final Security security, final ProcessorDao processorDao) {
        this.security = security;
        this.processorDao = processorDao;
    }

    @Override
    public Processor fetchInsecure(final int id) {
        return security.insecureResult(() -> fetch(id));
    }

    @Override
    public Processor create() {
        return security.secureResult(PERMISSION, processorDao::create);
    }

    @Override
    public Processor update(final Processor processor) {
        return security.secureResult(PERMISSION, () ->
                processorDao.update(processor));
    }

    @Override
    public int delete(final int id) {
        return security.secureResult(PERMISSION, () ->
                processorDao.delete(id));
    }

    @Override
    public Processor fetch(final int id) {
        return security.secureResult(() ->
                processorDao.fetch(id));
    }

    @Override
    public BaseResultList<Processor> find(final FindStreamProcessorCriteria criteria) {
        return security.secureResult(() ->
                processorDao.find(criteria));
    }

//    @Override
//    public Class<Processor> getEntityClass() {
//        return Processor.class;
//    }

//    @Override
//    public FindStreamProcessorCriteria createCriteria() {
//        return new FindStreamProcessorCriteria();
//    }
//
//    @Override
//    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindStreamProcessorCriteria criteria) {
//        CriteriaLoggingUtil.appendCriteriaSet(items, "pipelineSet", criteria.getPipelineSet());
//        super.appendCriteria(items, criteria);
//    }
//
//    @Override
//    public StreamProcessorQueryAppender createQueryAppender(final StroomEntityManager entityManager) {
//        return new StreamProcessorQueryAppender(entityManager);
//    }
//
//    @Override
//    protected String permission() {
//        return PermissionNames.MANAGE_PROCESSORS_PERMISSION;
//    }
//
//    private static class StreamProcessorQueryAppender extends QueryAppender<Processor, FindStreamProcessorCriteria> {
//        StreamProcessorQueryAppender(StroomEntityManager entityManager) {
//            super(entityManager);
//        }
//
//        @Override
//        protected void appendBasicCriteria(final HqlBuilder sql, final String entityName,
//                                           final FindStreamProcessorCriteria criteria) {
//            super.appendBasicCriteria(sql, entityName, criteria);
//            sql.appendDocRefSetQuery(entityName + ".pipelineUuid", criteria.getPipelineSet());
//        }
//    }
}
