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

package stroom.data.retention.impl;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableList;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.datasource.api.v2.DataSource;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.security.api.Security;
import stroom.util.RestResource;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Set;

/**
 * DataRetentionRules are a bit different: there's only ever one of them. First to clear up
 * some confusion over the names: DataRetentionRules (plural) is the data retention policy.
 * DataRetentionRule (singular) is an actual rule within the policy.
 *
 * The result of this is that we're only ever updating and returning a single entity, by adding
 * or removing the objects that compose it.
 */
@Api(value = "retention - /v1")
@Path("/retention/v1")
@Produces(MediaType.APPLICATION_JSON)
public class DataRetentionRulesResource implements RestResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataRetentionRulesResource.class);
    private final DataRetentionRulesService service;
    private final Security security;

    @Inject
    DataRetentionRulesResource(final DataRetentionRulesService dataRetentionRulesService,
                               final Security security) {
        this.service = dataRetentionRulesService;
        this.security = security;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    @Timed
    @ApiOperation(
            value = "Fetch the data retention policy and all its rules.",
            response = Set.class)
    public DataRetentionRules getPolicy() {
        // First we need to get or create the policy doc
        Set<DocRef> policies = service.listDocuments();
        DocRef policy;
        if(policies.isEmpty()){
            policy = service.createDocument("Data Retention Policy");
        } else {
            if(policies.size() != 1){
                LOGGER.error("Found more than one data retention policy! " +
                             "Returning the first.");
            }
            policy = policies.stream().findFirst().get();
        }

        // Then we can read and return the actual document.
        return service.readDocument(policy);
    }

    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    @Timed
    public void delete() {
        service.listDocuments().stream().forEach(docRef -> service.deleteDocument(docRef.getUuid()));
    }
//    @POST
//    @Consumes(MediaType.APPLICATION_JSON)
//    @Produces(MediaType.APPLICATION_JSON)
//    @Path("/copy/{uuid}")
//    @Timed
//    public DocRef copy(final @PathParam("uuid") String uuid) {
//        return service.copyDocument(uuid);
//    }
//    @POST
//    @Produces(MediaType.APPLICATION_JSON)
//    @Path("/")
//    @Timed
//    @ApiOperation(
//            value = "Create a new",
//            response = Set.class)
//    public DocRef create(@QueryParam("name") String name) {
//        return service.createDocument(name);
//    }

    //TODO: This creates a new policy, and there's only ever one policy.
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    @Timed
    @ApiOperation(
            value = "Create a new DataRetentionRules",
            response = Set.class)
    public DataRetentionRules create(DataRetentionRules dataRetentionRules) {
        DocRef docRef = service.createDocument(dataRetentionRules.getName());
        DataRetentionRules newRule = service.readDocument(docRef);
        dataRetentionRules.setType(docRef.getType());
        dataRetentionRules.setUuid(docRef.getUuid());
        dataRetentionRules.setName(docRef.getName());
        dataRetentionRules.setVersion(newRule.getVersion());
        return service.writeDocument(dataRetentionRules);
    }

    //TODO: This operation updates the policy, i.e. it can remove or add rules.
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    @Timed
    @ApiOperation(
            value = "Update a DataRetentionRules",
            response = Set.class)
    public DataRetentionRules update(DataRetentionRules dataRetentionRules) {
        return service.writeDocument(dataRetentionRules);
    }

    @GET
    @Path("/dataSource")
    @Produces(MediaType.APPLICATION_JSON)
    public Response dataSource() {
        DataSource dataSource = new DataSource(
                ImmutableList.of(new DataSourceField(
                        FIELD,
                        FeedDoc.DOCUMENT_TYPE,
                        "Feed",
                        true,
                        Arrays.asList(Condition.EQUALS, Condition.CONTAINS)
                )));

        return Response.ok(dataSource).build();
    }

    //    @POST
//    @Consumes(MediaType.APPLICATION_JSON)
//    @Produces(MediaType.APPLICATION_JSON)
//    @Path("/import")
//    @Timed
//    @ApiOperation(
//            value = "Submit an import request",
//            response = DocRef.class)
//    public DocRef importDocument(@ApiParam("DocumentData") final OldDocumentData documentData) {
//        final ImportState importState = new ImportState(documentData.getDocRef(), documentData.getDocRef().getName());
//        if (documentData.getDataMap() == null) {
//            return dictionaryStore.importDocument(documentData.getDocRef(), null, importState, ImportMode.IGNORE_CONFIRMATION);
//        }
//        final Map<String, byte[]> data = documentData.getDataMap().entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> EncodingUtil.asBytes(e.getValue())));
//        return dictionaryStore.importDocument(documentData.getDocRef(), data, importState, ImportMode.IGNORE_CONFIRMATION);
//    }
//
//    @POST
//    @Consumes(MediaType.APPLICATION_JSON)
//    @Produces(MediaType.APPLICATION_JSON)
//    @Path("/export")
//    @Timed
//    @ApiOperation(
//            value = "Submit an export request",
//            response = OldDocumentData.class)
//    public OldDocumentData exportDocument(@ApiParam("DocRef") final DocRef docRef) {
//        final Map<String, byte[]> map = dictionaryStore.exportDocument(docRef, true, new ArrayList<>());
//        if (map == null) {
//            return new OldDocumentData(docRef, null);
//        }
//        final Map<String, String> data = map.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> EncodingUtil.asString(e.getValue())));
//        return new OldDocumentData(docRef, data);
//    }
//
    private Response fetchInScope(final String dictionaryUuid) {
        final DataRetentionRules doc = service.readDocument(getDocRef(dictionaryUuid));
        return Response.ok(doc).build();
    }

    @GET
    @Path("/{uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response fetch(@PathParam("uuid") final String uuid) {
        return security.useAsReadResult(() -> fetchInScope(uuid));
    }

    private DocRef getDocRef(final String pipelineId) {
        return new DocRef.Builder()
                .uuid(pipelineId)
                .type(DictionaryDoc.ENTITY_TYPE)
                .build();
    }

//    @POST
//    @Path("/{uuid}")
//    @Produces(MediaType.APPLICATION_JSON)
//    @Consumes(MediaType.APPLICATION_JSON)
//    public Response save(@PathParam("uuid") final String uuid,
//                         final DataRetentionRules updates) {
//
//        // A user should be allowed to read pipelines that they are inheriting from as long as they have 'use' permission on them.
//        security.useAsRead(() -> {
//            final DataRetentionRules doc = service.readDocument(getDocRef(uuid));
//
//            if (doc != null) {
//                doc.setRules(updates.getRules());
//                service.writeDocument(doc);
//            }
//        });
//
//        return Response.noContent().build();
//    }


    /**
     * TODOs
     * store methods we care about:
     * readDocument
     * writeDocument
     * renameDoc8ument
     *
     */
}