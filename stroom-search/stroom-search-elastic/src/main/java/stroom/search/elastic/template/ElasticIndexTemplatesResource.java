/*
 * Copyright 2026 Crown Copyright
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

package stroom.search.elastic.template;

import stroom.search.elastic.shared.ElasticIndexResource;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/// Reads the index template and component templates that apply to an Elastic Index doc's index.
///
/// This deliberately does not live on [ElasticIndexResource], which extends DirectRestService, so GWT
/// generates a client proxy for every method on it and therefore has to be able to compile every
/// signature. The response carries Elasticsearch's own JSON bodies, which GWT cannot compile. Nothing in
/// the UI calls this endpoint, it being intended for the Stroom MCP server, so it lives here in the impl
/// module where GWT cannot see it.
///
/// It shares [ElasticIndexResource#BASE_PATH] so that it sits beside `testIndex`. RestResources flags two
/// resource classes declaring the same class level path, so this path is listed there as one that may be
/// shared.
@Tag(name = "Elastic Indices")
@Path(ElasticIndexResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ElasticIndexTemplatesResource extends RestResource {

    String TEMPLATES_PATH_PART = "/templates";

    /// Reads the index template and component templates that apply to an Elastic Index doc's index.
    ///
    /// Each part of the response reports its own outcome, so the response is 200 even if Elasticsearch
    /// refused to give up any of them.
    ///
    /// @param request The Elastic Index doc to read the templates for.
    /// @return The templates, as Elasticsearch returns them.
    @POST
    @Path(TEMPLATES_PATH_PART)
    @Operation(
            summary = "Read the index template and component templates that apply to an Elasticsearch index",
            description = "Resolves the doc's index name to the newest index or data stream it stands for, then "
                          + "reads the index template Elasticsearch would apply to it and each of that "
                          + "template's component templates, using the doc's cluster connection. Each part "
                          + "reports its own status (OK, NOT_FOUND, FORBIDDEN or ERROR), so a part that "
                          + "Elasticsearch refused does not fail the request. Requires READ on the doc.",
            operationId = "fetchElasticIndexTemplates")
    ElasticIndexTemplatesResponse fetchTemplates(
            @Parameter(description = "request", required = true) ElasticIndexTemplatesRequest request);
}
