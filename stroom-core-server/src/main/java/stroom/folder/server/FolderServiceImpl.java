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

package stroom.folder.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.server.GenericEntityService;
import stroom.entity.server.UserManagerQueryUtil;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocumentEntityService;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.FindFolderCriteria;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderIdSet;
import stroom.entity.shared.FolderService;
import stroom.importexport.server.ImportExportHelper;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.CompareUtil;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Transactional
@Component("folderService")
public class FolderServiceImpl extends DocumentEntityServiceImpl<Folder, FindFolderCriteria> implements FolderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FolderServiceImpl.class);

    private final GenericEntityService genericEntityService;
    private volatile String[] permissions;

    @Inject
    FolderServiceImpl(final StroomEntityManager entityManager, final ImportExportHelper importExportHelper, final SecurityContext securityContext, final GenericEntityService genericEntityService) {
        super(entityManager, importExportHelper, securityContext);
        this.genericEntityService = genericEntityService;
    }

    @Override
    public Class<Folder> getEntityClass() {
        return Folder.class;
    }

    @Override
    public FindFolderCriteria createCriteria() {
        return new FindFolderCriteria();
    }

    @Override
    public Folder save(Folder entity) throws RuntimeException {
        // If existing already check that any move is valid
        if (entity.isPersistent()) {
            final Folder origFolder = getEntityServiceHelper().load(entity);
            Long origParent = null;
            if (origFolder.getFolder() != null) {
                origParent = origFolder.getFolder().getId();
            }
            Long newParent = null;
            if (entity.getFolder() != null) {
                newParent = entity.getFolder().getId();
            }

            if (CompareUtil.compareLong(origParent, newParent) != 0) {
                // Some move... check new parent is not related to us
                Folder newParentGroup = getEntityServiceHelper().load(entity.getFolder());
                while (newParentGroup != null) {
                    if (newParentGroup.equals(entity)) {
                        throw new EntityServiceException(
                                "A folder cannot be a child of itself or of a descendant");
                    }
                    newParentGroup = getEntityServiceHelper().load(newParentGroup.getFolder());
                }
            }
        }

        return super.save(entity);
    }

    @Override
    public String[] getPermissions() {
        if (permissions == null) {
            final List<String> permissionList = new ArrayList<>();
            try {
                final Collection<DocumentEntityService<?>> serviceList = genericEntityService.findAll();
                for (final DocumentEntityService<?> service : serviceList) {
                    final BaseEntity e = service.getEntityClass().newInstance();

                    // Exclude queries as they aren't really entities that live in folders.
                    if (!"Query".equals(e.getType())) {
                        permissionList.add(DocumentPermissionNames.getDocumentCreatePermission(e.getType()));
                    }
                }
            } catch (final IllegalAccessException | InstantiationException | RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }

            Collections.sort(permissionList);

            permissionList.addAll(Arrays.asList(super.getPermissions()));

            String[] arr = new String[permissionList.size()];
            arr = permissionList.toArray(arr);
            permissions = arr;
        }

        return permissions;
    }
}
