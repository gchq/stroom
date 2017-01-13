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

package stroom.index.server;

import org.springframework.stereotype.Component;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.query.shared.DataSource;
import stroom.query.shared.DataSourceProvider;

import javax.annotation.Resource;

@Component
public class LuceneIndexDataSourceProvider implements DataSourceProvider {
    public static final String ENTITY_TYPE = Index.ENTITY_TYPE;

    @Resource
    private IndexService indexService;

    @Override
    public DataSource getDataSource(final String uuid) {
        final Index index = indexService.loadByUuid(uuid);
        return index;

        // TODO : Fix security

        //
        // // Constrain the folders that the user can see.
        // HasFolderIdSet criteria = new HasFolderIdSetImpl();
        // criteria = userSecuritySessionValidator
        // .constrainCriteria(criteria);
        // final FolderIdSet folderIdSet = criteria
        // .obtainFolderIdSet();
        // Folder folderToCheck = index.getFolder();
        // if (!folderIdSet.isMatch(folderToCheck)) {
        // folderToCheck = folderService.load(folderToCheck);
        // throw PermissionException.createPermissionRequiredException(
        // folderToCheck, index);
        // }
        //
        // return index.getIndexFieldsObject();
    }

    @Override
    public String getEntityType() {
        return ENTITY_TYPE;
    }
}
