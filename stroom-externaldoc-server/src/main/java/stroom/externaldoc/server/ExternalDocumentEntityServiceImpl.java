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

package stroom.externaldoc.server;

import org.eclipse.jetty.http.HttpStatus;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.SharedDocRef;
import stroom.importexport.shared.ImportState;
import stroom.logging.DocumentEventLog;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.ClientProperties;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.query.audit.ExportDTO;
import stroom.query.audit.client.DocRefResourceHttpClient;
import stroom.query.audit.security.ServiceUser;
import stroom.security.SecurityContext;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import javax.ws.rs.core.Response;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ExternalDocumentEntityServiceImpl implements ExternalDocumentEntityService {

    private final String type;
    private final SecurityContext securityContext;
    private final DocumentEventLog documentEventLog;
    private final DocRefResourceHttpClient docRefHttpClient;

    public ExternalDocumentEntityServiceImpl(final String type,
                                             final SecurityContext securityContext,
                                             final DocumentEventLog documentEventLog,
                                             final StroomPropertyService propertyService) {
        this.type = type;
        this.securityContext = securityContext;
        this.documentEventLog = documentEventLog;

        final String urlPropKey = ClientProperties.URL_DOC_REF_SERVICE_BASE + type;
        this.docRefHttpClient = new DocRefResourceHttpClient(propertyService.getProperty(urlPropKey));
    }

    private ServiceUser serviceUser() {
        return new ServiceUser.Builder()
                .jwt(securityContext.getApiToken())
                .name(securityContext.getUserId())
                .build();
    }

    @Override
    public String getType() {
        return type;
    }


    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    /**
     * Given a DocRef, it simply converts it to a SharedDocRef. This is as much as the core Stroom app
     * will need to know about an external Doc Ref in the general sense.
     * @param docRef The DocRef of the external object
     * @return A shared doc ref with the same details as the input
     */

    @Override
    public SharedDocRef readDocument(final DocRef docRef) {
        return new SharedDocRef.Builder()
                .uuid(docRef.getUuid())
                .name(docRef.getName())
                .type(docRef.getType())
                .build();
    }

    /**
     * Normally this is how the detail of a document are updated, but external DocRefs will be maintained by
     * external services with their own UI. So this is little more than a stub implementation to satisfy the need
     * to implement DocumentActionHandler.
     * @param document The document being written.
     * @return The input document
     */
    @Override
    public SharedDocRef writeDocument(final SharedDocRef document) {
        // Not really expecting this to happen
        return document;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public final DocRef createDocument(final String name, final String parentFolderUUID) {
        final String uuid = UUID.randomUUID().toString();
        final Response response = docRefHttpClient.createDocument(serviceUser(), uuid, name, parentFolderUUID);

        try {
            if (response.getStatus() != HttpStatus.OK_200) {
                throw new EntityServiceException("Invalid HTTP status returned from create: " + response.getStatus());
            }
        } finally {
            response.close();
        }

        return new DocRef.Builder()
                .uuid(uuid)
                .name(name)
                .type(type)
                .build();
    }

    @Override
    public DocRef copyDocument(final String uuid, final String parentFolderUUID) {
        final String copyUuid = UUID.randomUUID().toString();
        final Response response = docRefHttpClient.copyDocument(serviceUser(), uuid, copyUuid, parentFolderUUID);

        try {
            if (response.getStatus() != HttpStatus.OK_200) {
                throw new EntityServiceException("Invalid HTTP status returned from copy: " + response.getStatus());
            }
        } finally {
            response.close();
        }

        return new DocRef.Builder()
                .uuid(uuid)
                .type(this.type)
                .build();
    }

    @Override
    public DocRef moveDocument(final String uuid, final String parentFolderUUID) {
        final Response response = docRefHttpClient.moveDocument(serviceUser(), uuid, parentFolderUUID);

        try {
            if (response.getStatus() != HttpStatus.OK_200) {
                throw new EntityServiceException("Invalid HTTP status returned from move: " + response.getStatus());
            }
        } finally {
            response.close();
        }

        return new DocRef.Builder()
                .uuid(uuid)
                .type(this.type)
                .build();
    }

    @Override
    public DocRef renameDocument(final String uuid, final String name) {
        final Response response = docRefHttpClient.renameDocument(serviceUser(), uuid, name);

        try {
            if (response.getStatus() != HttpStatus.OK_200) {
                throw new EntityServiceException("Invalid HTTP status returned from rename: " + response.getStatus());
            }
        } finally {
            response.close();
        }

        return new DocRef.Builder()
                .uuid(uuid)
                .name(name)
                .type(this.type)
                .build();
    }

    @Override
    public void deleteDocument(final String uuid) {
        final Response response = docRefHttpClient.deleteDocument(serviceUser(), uuid);

        try {
            if (response.getStatus() != HttpStatus.OK_200) {
                throw new EntityServiceException("Invalid HTTP status returned from delete: " + response.getStatus());
            }
        } finally {
            response.close();
        }
    }

    @Override
    public DocRefInfo info(final String uuid) {
        final Response response = docRefHttpClient.getInfo(serviceUser(), uuid);

        try {
            if (response.getStatus() != HttpStatus.OK_200) {
                throw new EntityServiceException("Invalid HTTP status returned from move: " + response.getStatus());
            }

            return response.readEntity(DocRefInfo.class);
        } finally {
            response.close();
        }

    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ImportExport
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final Map<String, String> dataMap,
                                 final ImportState importState,
                                 final ImportState.ImportMode importMode) {
        final Response response = docRefHttpClient.importDocument(serviceUser(),
                docRef.getUuid(),
                docRef.getName(),
                importState.ok(importMode),
                dataMap);

        try {
            if (response.getStatus() != HttpStatus.OK_200) {
                throw new EntityServiceException("Invalid HTTP status returned from delete: " + response.getStatus());
            }

            return docRef;
        } finally {
            response.close();
        }
    }

    @Override
    public Map<String, String> exportDocument(final DocRef docRef, boolean omitAuditFields, List<Message> messageList) {
        final Response response = docRefHttpClient.exportDocument(serviceUser(), docRef.getUuid());

        final ExportDTO exportDTO = response.readEntity(ExportDTO.class);

        exportDTO.getMessages().stream()
                .map(m -> new Message(Severity.INFO, m))
                .forEach(messageList::add);

        return exportDTO.getValues();
    }

    @Override
    public Set<DocRef> listDocuments() {
        final Response response = docRefHttpClient.getAll(serviceUser());

        try {
            if (response.getStatus() != HttpStatus.OK_200) {
                throw new EntityServiceException("Invalid HTTP status returned from delete: " + response.getStatus());
            }

            return new HashSet<>();
        } finally {
            response.close();
        }
    }

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return null;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExport
    ////////////////////////////////////////////////////////////////////////
}