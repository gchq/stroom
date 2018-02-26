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

import org.springframework.transaction.annotation.Transactional;
import stroom.entity.CachingEntityManager;
import stroom.importexport.ImportExportHelper;
import stroom.security.SecurityContext;

import javax.inject.Inject;

@Transactional
class CachedPipelineService extends PipelineServiceImpl {
    @Inject
    CachedPipelineService(final CachingEntityManager entityManager,
                          final ImportExportHelper importExportHelper,
                          final SecurityContext securityContext) {
        super(entityManager, importExportHelper, securityContext);
    }
}
