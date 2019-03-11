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

package stroom.processor.impl;

import stroom.docref.DocRef;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.FindProcessorCriteria;
import stroom.processor.shared.Processor;
import stroom.util.shared.BaseResultList;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Mock object.
 * <p>
 * In memory simple process manager that also uses the mock stream store.
 */
@Singleton
public class MockProcessorService implements ProcessorService {
    @Override
    public Processor create(final DocRef pipelineRef, final boolean enabled) {
        return null;
    }

//    @Override
//    public Optional<Processor> fetchInsecure(final int id) {
//        return Optional.empty();
//    }
//
//    @Override
//    public Optional<Processor> fetchByUuid(final String uuid) {
//        return Optional.empty();
//    }

    @Override
    public BaseResultList<Processor> find(final FindProcessorCriteria criteria) {
        return null;
    }

    @Override
    public Processor create(final Processor processor) {
        return null;
    }

    @Override
    public Optional<Processor> fetch(final int id) {
        return Optional.empty();
    }

    @Override
    public Processor update(final Processor processor) {
        return null;
    }

    @Override
    public boolean delete(final int id) {
        return false;
    }

    ////    @Override
////    public Processor fetchInsecure(final long id) {
////        return loadById(id);
////    }
////
////    @Override
////    public Processor loadByUuid(final String uuid) {
////        // TODO : @66 IMPLEMENT
////        return null;
////    }
//
//    public boolean isMatch(final FindProcessorCriteria criteria, final Processor entity) {
////        if (!super.isMatch(criteria, entity)) {
////            return false;
////        }
//        return criteria.obtainPipelineUuidCriteria().isMatch(entity.getPipelineUuid());
//    }
//
////    @Override
////    public Class<Processor> getEntityClass() {
////        return Processor.class;
////    }
}
