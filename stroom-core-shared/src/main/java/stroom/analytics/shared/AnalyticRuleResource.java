/*
 * Copyright 2022 Crown Copyright
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

package stroom.analytics.shared;

import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.Message;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.string.StringWrapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;

@Tag(name = "Queries")
@Path("/analyticRule" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AnalyticRuleResource
        extends RestResource, DirectRestService, FetchWithUuid<AnalyticRuleDoc> {

    @GET
    @Path("/{uuid}")
    @Operation(
            summary = "Fetch an analytic rule doc by its UUID",
            operationId = "fetchAnalyticRule")
    AnalyticRuleDoc fetch(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update an analytic rule doc",
            operationId = "updateAnalyticRule")
    AnalyticRuleDoc update(@PathParam("uuid") String uuid,
                           @Parameter(description = "doc", required = true) AnalyticRuleDoc doc);

    @POST
    @Path("/validate")
    @Operation(
            summary = "Validates an analytic rule doc",
            operationId = "validateAnalyticRule")
    List<Message> validate(@Parameter(description = "doc", required = true) final AnalyticRuleDoc doc);

    @POST
    @Path("/testTemplate")
    @Operation(
            summary = "Tests the email template using an example detection event.",
            operationId = "testTemplate")
    StringWrapper testTemplate(@Parameter(description = "template", required = true) final StringWrapper template);

    @POST
    @Path("/sendTestEmail")
    @Operation(
            summary = "Tests the email subject/body templates using an example detection event.",
            operationId = "testEmailTemplates")
    void sendTestEmail(
            @Parameter(description = "emailDestination", required = true) final NotificationEmailDestination
                    analyticNotificationEmailDestination);
}
