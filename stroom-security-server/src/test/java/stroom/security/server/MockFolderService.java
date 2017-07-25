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

package stroom.security.server;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.entity.server.GenericEntityService;
import stroom.entity.server.MockDocumentEntityService;
import stroom.entity.shared.FindFolderCriteria;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.importexport.server.EntityPathResolver;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;

/**
 * <p>
 * Very simple mock user manager with just one user.
 * </p>
 */
@Profile(StroomSpringProfiles.TEST)
@Component("folderService")
public class MockFolderService extends MockDocumentEntityService<Folder, FindFolderCriteria> implements FolderService {
    @Inject
    public MockFolderService(final GenericEntityService genericEntityService, final EntityPathResolver entityPathResolver) {
        super(genericEntityService, entityPathResolver);
        setupTestUser();
    }

    /**
     * Used to reset the user.
     */
    public void setupTestUser() {
        create(null, "Junit Group");
    }

    @Override
    public Class<Folder> getEntityClass() {
        return Folder.class;
    }
}
