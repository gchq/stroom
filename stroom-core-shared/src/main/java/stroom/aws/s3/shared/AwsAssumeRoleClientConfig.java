/*
 * Copyright 2016-2025 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class AwsAssumeRoleClientConfig {
    @JsonProperty
    private final AwsCredentials credentials;
    @JsonProperty
    private final String region;
    @JsonProperty
    private final String endpointOverride;

    @JsonCreator
    public AwsAssumeRoleClientConfig(@JsonProperty("credentials") final AwsCredentials credentials,
                                     @JsonProperty("region") final String region,
                                     @JsonProperty("endpointOverride") final String endpointOverride) {
        this.credentials = credentials;
        this.region = region;
        this.endpointOverride = endpointOverride;
    }

    public AwsCredentials getCredentials() {
        return credentials;
    }

    public String getRegion() {
        return region;
    }

    public String getEndpointOverride() {
        return endpointOverride;
    }
}
