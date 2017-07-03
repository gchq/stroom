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

package stroom.explorer.server;

import org.springframework.aop.framework.Advised;
import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.shared.*;
import stroom.entity.shared.Sort.Direction;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.EntityData;
import stroom.explorer.shared.ExplorerData;
import stroom.folder.server.FolderExplorerDataProvider;
import stroom.folder.server.FolderRootExplorerDataProvider;
import stroom.util.logging.StroomLogger;

import javax.inject.Named;

public abstract class AbstractExplorerDataProvider<E extends DocumentEntity, C extends FindDocumentEntityCriteria>
        implements ExplorerDataProvider {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(AbstractExplorerDataProvider.class);

    private final FolderService folderService;

    public AbstractExplorerDataProvider(@Named("cachedFolderService") FolderService folderService) {
        this.folderService = folderService;
    }

    public void addItems(final FindService<E, C> findService, final TreeModel treeModel) {
        addItems(findService, treeModel, findService.createCriteria());
    }

    public void addItems(final FindService<E, C> findService,
                         final TreeModel treeModel, final C criteria) {
        if (criteria != null) {
            criteria.setSort(FindNamedEntityCriteria.FIELD_NAME);
        }

        // TODO : This is a temporary fudge until the separate explorer service is created - we shouldn't need to poke insecure holes in the document service.
        final DocumentEntityServiceImpl documentEntityService = getDocumentEntityService(findService);
        if (documentEntityService != null) {
            final BaseResultList<E> list = documentEntityService.findInsecure(criteria);
            addItems(list, treeModel);

        } else {
            final BaseResultList<E> list = findService.find(criteria);
            addItems(list, treeModel);
        }
    }

    private void addItems(final BaseResultList<E> list, final TreeModel treeModel) {
        for (final E entity : list) {
            // Get parent explorer data.
            ExplorerData parent = FolderRootExplorerDataProvider.ROOT;
            Folder folder = entity.getFolder();
            if (folder != null) {
                // TODO : This is a temporary fudge until the separate explorer service is created - we shouldn't need to poke insecure holes in the document service.
                final DocumentEntityServiceImpl documentEntityService = getDocumentEntityService(folderService);
                if (documentEntityService != null) {
                    folder = (Folder) documentEntityService.loadByIdInsecure(folder.getId(), null);
                    parent = EntityData.create(FolderExplorerDataProvider.ICON_URL, folder);
                }
            }

            // Get entity explorer data.
            final EntityData entityData = createEntityData(entity);
            treeModel.add(parent, entityData);
        }
    }

    protected EntityData createEntityData(E entity) {
        return EntityData.create(getIconUrl(), entity);
    }

    @Override
    public String getIconUrl() {
        return DocumentType.DOC_IMAGE_URL + getType() + ".png";
    }

    // TODO : This is a temporary fudge until the separate explorer service is created.
    private DocumentEntityServiceImpl getDocumentEntityService(final Object obj) {
        final Object unwrapped = unwrapProxy(obj);
        if (unwrapped instanceof DocumentEntityServiceImpl) {
            return (DocumentEntityServiceImpl) unwrapped;
        }

        return null;
    }

    private Object unwrapProxy(final Object obj) {
        Object unwrapped = obj;

        if (obj instanceof Advised) {
            try {
                final Advised advised = (Advised) obj;
                final Object target = advised.getTargetSource().getTarget();
                unwrapped = unwrapProxy(target);
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        return unwrapped;
    }
}
