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

package stroom.entity.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.http.HttpStatus;
import org.springframework.stereotype.Component;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.SharedDocRef;
import stroom.importexport.server.ImportExportHelper;
import stroom.importexport.shared.ImportState;
import stroom.logging.DocumentEventLog;
import stroom.node.server.StroomPropertyService;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.query.audit.DocRefResourceHttpClient;
import stroom.query.audit.ExportDTO;
import stroom.security.SecurityContext;
import stroom.util.shared.Message;
import stroom.util.shared.QueryApiException;
import stroom.util.shared.Severity;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ExternalDocumentEntityServiceImpl implements ExternalDocumentEntityService {
    public static final String BASE_URL_PROPERTY = "stroom.url.doc-ref.%s";

    private final String type;
    private final ImportExportHelper importExportHelper;
    private final SecurityContext securityContext;
    private final DocumentEventLog documentEventLog;
    private final DocRefResourceHttpClient docRefHttpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExternalDocumentEntityServiceImpl(final String type,
                                             final ImportExportHelper importExportHelper,
                                             final SecurityContext securityContext,
                                             final DocumentEventLog documentEventLog,
                                             final StroomPropertyService propertyService) {
        this.type = type;
        this.importExportHelper = importExportHelper;
        this.securityContext = securityContext;
        this.documentEventLog = documentEventLog;

        final String urlPropKey = String.format(BASE_URL_PROPERTY, type);
        this.docRefHttpClient = new DocRefResourceHttpClient(propertyService.getProperty(urlPropKey));
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
        try {
            final String uuid = UUID.randomUUID().toString();
            final Response response = docRefHttpClient.createDocument(uuid, name);

            if (response.getStatus() != HttpStatus.OK_200) {
                throw new QueryApiException(new Exception("Invalid HTTP status returned from create: " + response.getStatus()));
            }

            return new DocRef.Builder()
                    .uuid(uuid)
                    .name(name)
                    .type(type)
                    .build();
        } catch (final QueryApiException e) {
            throw new EntityServiceException("Could not create entity: " + e.getLocalizedMessage());
        }

    }

    @Override
    public DocRef copyDocument(final String uuid, final String parentFolderUUID) {
        try {
            final String copyUuid = UUID.randomUUID().toString();
            final Response response = docRefHttpClient.copyDocument(uuid, copyUuid);

            if (response.getStatus() != HttpStatus.OK_200) {
                throw new QueryApiException(new Exception("Invalid HTTP status returned from copy: " + response.getStatus()));
            }

            return new DocRef.Builder()
                    .uuid(uuid)
                    .type(this.type)
                    .build();
        } catch (final QueryApiException e) {
            throw new EntityServiceException("Could not create entity: " + e.getLocalizedMessage());
        }
    }

    @Override
    public DocRef moveDocument(final String uuid, final String parentFolderUUID) {
        try {
            final Response response = docRefHttpClient.documentMoved(uuid);

            if (response.getStatus() != HttpStatus.OK_200) {
                throw new QueryApiException(new Exception("Invalid HTTP status returned from move: " + response.getStatus()));
            }

            return new DocRef.Builder()
                    .uuid(uuid)
                    .type(this.type)
                    .build();
        } catch (final QueryApiException e) {
            throw new EntityServiceException("Could not create entity: " + e.getLocalizedMessage());
        }
    }

    @Override
    public DocRef renameDocument(final String uuid, final String name) {
        try {
            final Response response = docRefHttpClient.documentRenamed(uuid, name);

            if (response.getStatus() != HttpStatus.OK_200) {
                throw new QueryApiException(new Exception("Invalid HTTP status returned from rename: " + response.getStatus()));
            }

            return new DocRef.Builder()
                    .uuid(uuid)
                    .name(name)
                    .type(this.type)
                    .build();
        } catch (final QueryApiException e) {
            throw new EntityServiceException("Could not create entity: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void deleteDocument(final String uuid) {
        try {
            final Response response = docRefHttpClient.deleteDocument(uuid);

            if (response.getStatus() != HttpStatus.NO_CONTENT_204) {
                throw new QueryApiException(new Exception("Invalid HTTP status returned from delete: " + response.getStatus()));
            }
        } catch (final QueryApiException e) {
            throw new EntityServiceException("Could not create entity: " + e.getLocalizedMessage());
        }
    }

    @Override
    public DocRefInfo info(final String uuid) {
        try {
            final Response response = docRefHttpClient.getInfo(uuid);

            if (response.getStatus() != HttpStatus.OK_200) {
                throw new QueryApiException(new Exception("Invalid HTTP status returned from getInfo: " + response.getStatus()));
            }

            return objectMapper.readValue(response.getEntity().toString(), DocRefInfo.class);
        } catch (final QueryApiException | IOException e) {
            throw new EntityServiceException("Could not create entity: " + e.getLocalizedMessage());
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
        try {
            docRefHttpClient.importDocument(docRef.getUuid(), docRef.getName(), importState.ok(importMode), dataMap);

            return docRef;
        } catch (final QueryApiException e) {
            throw new EntityServiceException("Could not import entity: " + e.getLocalizedMessage());
        }
    }

    @Override
    public Map<String, String> exportDocument(final DocRef docRef, boolean omitAuditFields, List<Message> messageList) {
        try {
            final Response response = docRefHttpClient.exportDocument(docRef.getUuid());

            final ExportDTO exportDTO = objectMapper.readValue(response.getEntity().toString(), ExportDTO.class);

            exportDTO.getMessages().stream()
                    .map(m -> new Message(Severity.INFO, m))
                    .forEach(messageList::add);

            return exportDTO.getValues();
        } catch (final QueryApiException | IOException e) {
            throw new EntityServiceException("Could not import entity: " + e.getLocalizedMessage());
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExport
    ////////////////////////////////////////////////////////////////////////
}