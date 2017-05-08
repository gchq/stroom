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

package stroom.streamtask.server;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.entity.server.MockEntityService;
import stroom.streamtask.shared.FindStreamProcessorCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorService;
import stroom.util.spring.StroomSpringProfiles;

/**
 * Mock object.
 * <p>
 * In memory simple process manager that also uses the mock stream store.
 */
@Profile(StroomSpringProfiles.TEST)
@Component("streamProcessorService")
public class MockStreamProcessorService extends MockEntityService<StreamProcessor, FindStreamProcessorCriteria>
        implements StreamProcessorService {
    @Override
    public StreamProcessor loadByIdInsecure(final long id) throws RuntimeException {
        return loadById(id);
    }

    @Override
    public boolean isMatch(final FindStreamProcessorCriteria criteria, final StreamProcessor entity) {
        if (!super.isMatch(criteria, entity)) {
            return false;
        }
        return criteria.obtainPipelineIdSet().isMatch(entity.getPipeline());
    }

    @Override
    public Class<StreamProcessor> getEntityClass() {
        return StreamProcessor.class;
    }
}
