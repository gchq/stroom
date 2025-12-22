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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @Type(value = AwsAnonymousCredentials.class, name = "anonymous"),
        @Type(value = AwsBasicCredentials.class, name = "basic"),
        @Type(value = AwsDefaultCredentials.class, name = "default"),
        @Type(value = AwsEnvironmentVariableCredentials.class, name = "environment"),
        @Type(value = AwsProfileCredentials.class, name = "profile"),
        @Type(value = AwsSessionCredentials.class, name = "session"),
        @Type(value = AwsSystemPropertyCredentials.class, name = "system"),
        @Type(value = AwsWebCredentials.class, name = "web")
})
public sealed interface AwsCredentials permits
        AwsAnonymousCredentials,
        AwsBasicCredentials,
        AwsDefaultCredentials,
        AwsEnvironmentVariableCredentials,
        AwsProfileCredentials,
        AwsSessionCredentials,
        AwsSystemPropertyCredentials,
        AwsWebCredentials {
    // TODO: Make sealed class when GWT supports them.
}
