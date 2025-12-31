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

package stroom.ai.shared;

import stroom.docref.DocRef;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "AI")
@Path(AskStroomAiResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AskStroomAiResource extends RestResource, DirectRestService {

    String BASE_PATH = "/ai" + ResourcePaths.V1;

    String NODE_NAME_PATH_PARAM = "/{nodeName}";
    String ASK_STROOM_AI_PATH_PART = "/askStroomAi";

    @POST
    @Path(ASK_STROOM_AI_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Ask Stroom AI a question relating to the current search context",
            operationId = "askStroomAi")
    AskStroomAiResponse askStroomAi(
            @PathParam("nodeName") String nodeName,
            @Parameter(description = "request", required = true) final AskStroomAiRequest request);

    @POST
    @Path(ASK_STROOM_AI_PATH_PART)
    @Operation(
            summary = "Ask Stroom AI a question relating to the current search context",
            operationId = "askStroomAi")
    default AskStroomAiResponse askStroomAi(
            @Parameter(description = "request", required = true) final AskStroomAiRequest request) {
        return askStroomAi(null, request);
    }

    @POST
    @Path("/getDefaultConfig")
    @Operation(
            summary = "Get the default config to use for asking questions",
            operationId = "getDefaultConfig")
    AskStroomAIConfig getDefaultConfig();

    @POST
    @Path("/setDefaultModel")
    @Operation(
            summary = "Set the default model to use for asking questions",
            operationId = "setDefaultModel")
    Boolean setDefaultModel(@Parameter(description = "modelRef", required = true) final DocRef modelRef);

    @POST
    @Path("/setDefaultTableSummaryConfig")
    @Operation(
            summary = "Set the default table summary config to use for asking questions",
            operationId = "setDefaultTableSummaryConfig")
    Boolean setDefaultTableSummaryConfig(
            @Parameter(description = "config", required = true) final TableSummaryConfig config);

    @POST
    @Path("/setDefaultChatMemoryConfigConfig")
    @Operation(
            summary = "Set the default chat memory config to use for asking questions",
            operationId = "setDefaultChatMemoryConfigConfig")
    Boolean setDefaultChatMemoryConfigConfig(
            @Parameter(description = "config", required = true) final ChatMemoryConfig config);
}
