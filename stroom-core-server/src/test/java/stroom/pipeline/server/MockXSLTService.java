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

package stroom.pipeline.server;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.entity.server.GenericEntityService;
import stroom.entity.server.MockDocumentEntityService;
import stroom.importexport.server.EntityPathResolver;
import stroom.pipeline.shared.FindXSLTCriteria;
import stroom.pipeline.shared.XSLT;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;

/**
 * <p>
 * Very simple mock that keeps everything in memory.
 * </p>
 * <p>
 * <p>
 * You can call clear at any point to clear everything down.
 * </p>
 */
@Profile(StroomSpringProfiles.TEST)
@Component
public class MockXSLTService extends MockDocumentEntityService<XSLT, FindXSLTCriteria> implements XSLTService {
    @Inject
    public MockXSLTService(final GenericEntityService genericEntityService, final EntityPathResolver entityPathResolver) {
        super(genericEntityService, entityPathResolver);
    }

    @Override
    public Class<XSLT> getEntityClass() {
        return XSLT.class;
    }
}
