/*
 * Copyright 2025 Crown Copyright
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

package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AccessTokenSecret.class, name = "accessToken"),
        @JsonSubTypes.Type(value = KeyStoreSecret.class, name = "keyStore"),
        @JsonSubTypes.Type(value = SshKeySecret.class, name = "sshKey"),
        @JsonSubTypes.Type(value = UsernamePasswordSecret.class, name = "usernamePassword")
})
@Schema(
        discriminatorProperty = "type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "accessToken", schema = AccessTokenSecret.class),
                @DiscriminatorMapping(value = "keyStore", schema = KeyStoreSecret.class),
                @DiscriminatorMapping(value = "sshKey", schema = SshKeySecret.class),
                @DiscriminatorMapping(value = "usernamePassword", schema = UsernamePasswordSecret.class)})
public sealed interface Secret permits
        AccessTokenSecret,
        KeyStoreSecret,
        SshKeySecret,
        UsernamePasswordSecret {

}
