/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.aws.s3.shared;


import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Tag(name = "S3 Event Resource")
@Path(S3EventResource.BASE_RESOURCE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface S3EventResource extends RestResource {

    String BASE_RESOURCE_PATH = "/s3event" + ResourcePaths.V1;
    String NOTIFY_PATH_PART = "/notify";

    @POST
    @Path(NOTIFY_PATH_PART)
    @Operation(
            summary = "Submit a notification of the presence of a data file on S3",
            operationId = "notify")
    void notify(@Parameter(description = "S3EventRequest", required = true) S3EventNotificationRequest request);


    // --------------------------------------------------------------------------------


    /**
     * Used for notifying Stroom of the presence of a data file on S3. This is an alternative to relying
     * on Event Notifications produced by the S3 service.
     */
    @JsonPropertyOrder(alphabetic = true)
    @JsonInclude(Include.NON_NULL)
    class S3EventNotificationRequest {

        @JsonProperty
        private final S3Location s3Location;
        @JsonProperty
        private final Map<String, String> metaData;

        @JsonCreator
        public S3EventNotificationRequest(@JsonProperty("s3Location") final S3Location s3Location,
                                          @JsonProperty("metaData") final Map<String, String> metaData) {
            this.s3Location = s3Location;
            this.metaData = metaData;
        }

        /**
         * @return The location of the file in S3.
         */
        public S3Location getS3Location() {
            return s3Location;
        }

        /**
         * Additional metadata for the s3 object. Case-insensitive keys.
         * Any keys in here that also in the s3 object metadata, will trump the s3 object metadata.
         */
        public Map<String, String> getMetaData() {
            return metaData;
        }

        @Override
        public String toString() {
            return "S3EventRequest{" +
                   "s3Location=" + s3Location +
                   ", metaData=" + metaData +
                   '}';
        }
    }
}
