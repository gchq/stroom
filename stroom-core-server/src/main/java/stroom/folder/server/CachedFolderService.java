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

package stroom.folder.server;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.CachingEntityManager;
import stroom.entity.server.GenericEntityService;
import stroom.importexport.server.ImportExportHelper;
import stroom.security.SecurityContext;

import javax.inject.Inject;

@Transactional
@Component("cachedFolderService")
public class CachedFolderService extends FolderServiceImpl {
    @Inject
    public CachedFolderService(final CachingEntityManager entityManager, final ImportExportHelper importExportHelper, final SecurityContext securityContext, final GenericEntityService genericEntityService) {
        super(entityManager, importExportHelper, securityContext, genericEntityService);
    }
}
