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

package stroom.ruleset.server;

import org.springframework.stereotype.Component;
import stroom.document.server.fs.FSDocumentStore;
import stroom.entity.shared.PermissionInheritance;
import stroom.query.api.v1.DocRef;
import stroom.ruleset.shared.RuleSet;
import stroom.security.SecurityContext;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Set;

@Component
public class RuleSetServiceImpl implements RuleSetService {
    private final FSDocumentStore<RuleSet> documentStore;

    @Inject
    public RuleSetServiceImpl(final SecurityContext securityContext) throws IOException {
        documentStore = new FSDocumentStore<>(Paths.get("/Users/stroomdev66/tmp/config/" + RuleSet.DOCUMENT_TYPE), RuleSet.DOCUMENT_TYPE, RuleSet.class, securityContext);
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef create(final String parentFolderUUID, final String name) {
        return documentStore.create(parentFolderUUID, name);
    }

    @Override
    public DocRef copy(final String uuid, final String parentFolderUUID) {
        return documentStore.copy(uuid, parentFolderUUID);
    }

    @Override
    public DocRef move(final String uuid, final String parentFolderUUID) {
        return documentStore.move(uuid, parentFolderUUID);
    }

    @Override
    public DocRef rename(final String uuid, final String name) {
        return documentStore.rename(uuid, name);
    }

    @Override
    public void delete(final String uuid) {
        documentStore.delete(uuid);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Object read(final DocRef docRef) {
        return documentStore.read(docRef);
    }

    @Override
    public Object write(final Object document) {
        return documentStore.write(document);
    }

    @Override
    public Object fork(final Object document, final String docName, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        return documentStore.fork(document, docName, destinationFolderRef, permissionInheritance);
    }

    @Override
    public void delete(final DocRef docRef) {
        documentStore.delete(docRef);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////


    @Override
    public String getDocType() {
        return RuleSet.DOCUMENT_TYPE;
    }

    @Override
    public RuleSet read(final String uuid) {
        return documentStore.read(uuid);
    }

    @Override
    public RuleSet update(final RuleSet dataReceiptPolicy) {
        return documentStore.update(dataReceiptPolicy);
    }

    // TODO : This is a temporary fudge until the separate explorer service is created.
    Set<RuleSet> list() {
        return documentStore.list();
    }
}
