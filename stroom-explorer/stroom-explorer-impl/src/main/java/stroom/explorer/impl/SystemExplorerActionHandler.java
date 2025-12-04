/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.UniqueNameUtil;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.shared.ExplorerConstants;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class SystemExplorerActionHandler implements ExplorerActionHandler {

    private static final String FOLDER = ExplorerConstants.FOLDER_TYPE;

    private final SecurityContext securityContext;
    private final ExplorerTreeDao explorerTreeDao;

    @Inject
    SystemExplorerActionHandler(final SecurityContext securityContext,
                                final ExplorerTreeDao explorerTreeDao) {
        this.securityContext = securityContext;
        this.explorerTreeDao = explorerTreeDao;
    }

    @Override
    public DocRef createDocument(final String name) {
        throw new PermissionException(securityContext.getUserRef(), "You cannot create the System node");
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final ExplorerTreeNode explorerTreeNode = explorerTreeDao.findByUUID(docRef.getUuid());
        if (explorerTreeNode == null) {
            throw new RuntimeException("Unable to find tree node to copy");
        }

        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to read (" + FOLDER + ")");
        }

        String folderName = name;
        if (folderName == null || folderName.trim().isEmpty()) {
            folderName = explorerTreeNode.getName();
        }

        final String newName = UniqueNameUtil.getCopyName(folderName, makeNameUnique, existingNames);
        return new DocRef(FOLDER, UUID.randomUUID().toString(), newName);
    }

    @Override
    public DocRef moveDocument(final DocRef docRef) {
        throw new PermissionException(securityContext.getUserRef(), "You cannot move the System node");
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        throw new PermissionException(securityContext.getUserRef(), "You cannot rename the System node");
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        throw new PermissionException(securityContext.getUserRef(), "You cannot delete the System node");
    }

    @Override
    public DocRefInfo info(final DocRef docRef) {
        throw new PermissionException(
                securityContext.getUserRef(),
                "You cannot get info about the System node");
    }

    @Override
    public String getType() {
        return ExplorerConstants.SYSTEM;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return Collections.emptyMap();
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        return Collections.emptySet();
    }

    @Override
    public void remapDependencies(final DocRef docRef, final Map<DocRef, DocRef> remappings) {
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF HasDependencies
    ////////////////////////////////////////////////////////////////////////


    @Override
    public List<DocRef> findByNames(final List<String> name, final boolean allowWildCards) {
        throw new PermissionException(securityContext.getUserRef(),
                "You cannot perform findByNames on the System node handler");
    }

    @Override
    public Set<DocRef> listDocuments() {
        throw new PermissionException(securityContext.getUserRef(),
                "You cannot perform listDocuments on the System node handler");
    }
}
