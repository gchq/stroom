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

package stroom.pipeline.xslt;

import com.codahale.metrics.health.HealthCheck.Result;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.pipeline.shared.XsltDTO;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.XsltResource;
import stroom.security.api.SecurityContext;
import stroom.util.HasHealthCheck;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

class XsltResourceImpl implements XsltResource, HasHealthCheck {
    private final XsltStore xsltStore;
    private final DocumentResourceHelper documentResourceHelper;
    private SecurityContext securityContext;

    @Inject
    XsltResourceImpl(final XsltStore xsltStore,
                     final DocumentResourceHelper documentResourceHelper,
                     final SecurityContext securityContext) {
        this.xsltStore = xsltStore;
        this.documentResourceHelper = documentResourceHelper;
        this.securityContext = securityContext;
    }

    @Override
    public XsltDoc read(final DocRef docRef) {
        return documentResourceHelper.read(xsltStore, docRef);
    }

    @Override
    public XsltDoc update(final XsltDoc doc) {
        return documentResourceHelper.update(xsltStore, doc);
    }



    @GET
    @Path("/{xsltId}")
    public Response fetch(@PathParam("xsltId") final String xsltId) {
        return securityContext.secureResult(() -> {
            final XsltDoc xsltDoc = xsltStore.readDocument(getDocRef(xsltId));
            if (null != xsltDoc) {
                return Response.ok(new XsltDTO(xsltDoc)).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        });
    }

    @POST
    @Path("/{xsltId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response save(@PathParam("xsltId") final String xsltId,
                         final XsltDTO xsltDto) {
        // A user should be allowed to read pipelines that they are inheriting from as long as they have 'use' permission on them.
        securityContext.useAsRead(() -> {
            final XsltDoc xsltDoc = xsltStore.readDocument(getDocRef(xsltId));

            if (xsltDoc != null) {
                xsltDoc.setDescription(xsltDto.getDescription());
                xsltDoc.setData(xsltDto.getData());
                xsltStore.writeDocument(xsltDoc);
            }
        });

        return Response.noContent().build();
    }
    @Override
    public Result getHealth() {
        return Result.healthy();
    }

    private DocRef getDocRef(final String xsltId) {
        return new DocRef.Builder()
                .uuid(xsltId)
                .type(XsltDoc.DOCUMENT_TYPE)
                .build();
    }

}