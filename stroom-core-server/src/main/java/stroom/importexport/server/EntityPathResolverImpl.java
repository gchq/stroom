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

package stroom.importexport.server;

import org.springframework.stereotype.Component;
import stroom.entity.server.GenericEntityService;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.Folder;
import stroom.entity.shared.HasFolder;
import stroom.entity.shared.NamedEntity;

import javax.annotation.Resource;
import java.util.Set;

@Component
public class EntityPathResolverImpl implements EntityPathResolver {
    @Resource
    private GenericEntityService entityLoader;

    @Override
    public <E extends NamedEntity> String getEntityPath(final String entityType, final BaseEntity relative,
                                                        final E entity) {
        final StringBuilder path = new StringBuilder();

        // Append the name of the entity.
        path.append(entity.getName());

        if (entity instanceof HasFolder) {
            // If the entity belongs to a folder then add the folder path.
            Folder folder = entityLoader.load(((HasFolder) entity).getFolder());

            // Get all ancestor folders.
            while (folder != null) {
                path.insert(0, "/");
                path.insert(0, folder.getName());
                folder = entityLoader.load(folder.getFolder());
            }
            path.insert(0, "/");

            // If the path should be relative to another entity then create a relative path.
            if (relative != null) {
                Folder relativeFolder = null;
                if (relative instanceof Folder) {
                    relativeFolder = (Folder) relative;

                } else if (relative instanceof HasFolder) {
                    // The relative entity isn't a folder so use its parent folder to make a relative path.
                    final HasFolder hasFolder = (HasFolder) relative;
                    relativeFolder = hasFolder.getFolder();
                    if (relativeFolder != null) {
                        // Load the parent group.
                        relativeFolder = entityLoader.load(relativeFolder);
                    }
                }

                if (relativeFolder != null) {
                    // If we have a relative folder that matches at least part of the path of this entity then subtract
                    // the path of the relative folder.
                    final String relativePath = getEntityPath(Folder.ENTITY_TYPE, null, relativeFolder);
                    if (relativePath != null && path.toString().startsWith(relativePath)) {
                        return path.substring(relativePath.length() + 1);
                    }
                }
            }
        }

        return path.toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E extends NamedEntity> E getEntity(final String entityType, final BaseEntity relative, final String path,
                                               final Set<String> fetchSet) {
        Folder folder = null;
        if (relative instanceof Folder) {
            folder = (Folder) relative;
        } else {
            if (relative instanceof HasFolder) {
                folder = ((HasFolder) relative).getFolder();
            }
        }

        final String[] pathParts = path.split("/");
        for (int i = 0; i < pathParts.length - 1; i++) {
            folder = entityLoader.loadByName(Folder.ENTITY_TYPE, DocRefUtil.create(folder), pathParts[i]);
        }

        final String name = pathParts[pathParts.length - 1];

        entityLoader.getEntityService(entityType);

        return (E) entityLoader.loadByName(entityType, DocRefUtil.create(folder), name, fetchSet);
    }
}
