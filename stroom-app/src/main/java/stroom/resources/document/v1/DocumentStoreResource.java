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

package stroom.resources.document.v1;

import com.codahale.metrics.annotation.Timed;
import stroom.document.DocumentStore;
import stroom.query.api.v2.DocRef;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("document")
@Produces(MediaType.APPLICATION_JSON)
public class DocumentStoreResource {
    private final DocumentStore documentStore;

    @Inject
    public DocumentStoreResource(final DocumentStore documentStore) {
        this.documentStore = documentStore;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("")
    @Timed
    public DocRef create(final String name) {
        return documentStore.createDocument(name);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/copy/{uuid}")
    @Timed
    public DocRef copy(final @PathParam("uuid") String uuid) {
        return documentStore.copyDocument(uuid);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/move/{uuid}")
    @Timed
    public DocRef move(final @PathParam("uuid") String uuid) {
        return documentStore.moveDocument(uuid);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/rename/{uuid}")
    @Timed
    public DocRef rename(final @PathParam("uuid") String uuid, final String name) {
        return documentStore.renameDocument(uuid, name);
    }


//    @GET
//    @Consumes(MediaType.APPLICATION_JSON)
//    @Produces(MediaType.APPLICATION_JSON)
//    @Path("/{uuid}")
//    @Timed
//    public Document read(final @PathParam("uuid") String uuid) {
//        return documentStore.read(uuid);
//    }
//
//    @PUT
//    @Consumes(MediaType.APPLICATION_JSON)
//    @Produces(MediaType.APPLICATION_JSON)
//    @Path("/{uuid}")
//    @Timed
//    public Document update(final @PathParam("uuid") String uuid, final Document document) {
//        return dataReceiptService.update(uuid, document);
//    }

    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{uuid}")
    @Timed
    public void delete(final @PathParam("uuid") String uuid) {
        documentStore.deleteDocument(uuid);
    }
}