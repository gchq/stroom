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

package stroom.externaldoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.jetty.http.HttpStatus;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.SharedDocRef;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.shared.ImportState;
import stroom.logging.DocumentEventLog;
import stroom.properties.StroomPropertyService;
import stroom.node.shared.ClientProperties;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.query.audit.ExportDTO;
import stroom.query.audit.client.DocRefResourceHttpClient;
import stroom.query.audit.security.ServiceUser;
import stroom.query.audit.service.DocRefEntity;
import stroom.security.SecurityContext;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        return readDocRefEntityResponse(response);
    }

    @Override
    public DocRef copyDocument(final String originalUuid,
                               final String copyUuid,
                               final Map<String, String> otherCopiesByOriginalUuid,
                               final String parentFolderUUID) {
        final Response response = docRefHttpClient.copyDocument(
                serviceUser(),
                originalUuid,
                copyUuid,
                parentFolderUUID);

        return readDocRefEntityResponse(response);
    }

    @Override
    public DocRef moveDocument(final String uuid, final String parentFolderUUID) {
        final Response response = docRefHttpClient.moveDocument(serviceUser(), uuid, parentFolderUUID);

        return readDocRefEntityResponse(response);
    }

    @Override
    public DocRef renameDocument(final String uuid, final String name) {
        final Response response = docRefHttpClient.renameDocument(serviceUser(), uuid, name);

        return readDocRefEntityResponse(response);
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

        if (response.getStatus() != HttpStatus.OK_200) {
            response.close();
            throw new EntityServiceException("Invalid HTTP status returned from move: " + response.getStatus());
        }

        return response.readEntity(DocRefInfo.class);
    }

    @Override
    public DocumentType getDocumentType() {
        return new DocumentType(30, getType(), getType());
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

        return readDocRefEntityResponse(response);
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

        if (response.getStatus() != HttpStatus.OK_200) {
            response.close();
            throw new EntityServiceException("Invalid HTTP status returned from delete: " + response.getStatus());
        }


        final List<DocRefEntity> results = response.readEntity(new GenericType<List<DocRefEntity>>(){});

        return results.stream()
                .map(this::getDocRef)
                .collect(Collectors.toSet());
    }

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return listDocuments().stream().collect(Collectors.toMap(Function.identity(), d -> Collections.emptySet()));
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExport
    ////////////////////////////////////////////////////////////////////////

    /**
     * This class soley exists to allow us to deserialize the JSON to a generic Doc Ref Entity while ignoring
     * properties which are specific to the doc ref entity type.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocRefEntitySafe extends DocRefEntity {
        public DocRefEntitySafe() {

        }
    }

    /**
     * General form of parsing a response into a Doc Ref
     * @param response The Response from the client, should be HTTP 200, with the Doc Ref Entity in the body
     * @return The Doc Ref, created from the Doc Ref Entity
     */
    private DocRef readDocRefEntityResponse(final Response response) {
        if (response.getStatus() != HttpStatus.OK_200) {
            response.close();
            throw new EntityServiceException("Invalid HTTP status returned from rename: " + response.getStatus());
        }

        final DocRefEntity docRefEntity = response.readEntity(DocRefEntitySafe.class);

        return getDocRef(docRefEntity);
    }

    private DocRef getDocRef(final DocRefEntity entity) {
        return new DocRef.Builder()
                .uuid(entity.getUuid())
                .name(entity.getName())
                .type(this.type)
                .build();
    }
}