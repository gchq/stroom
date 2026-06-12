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

package stroom.importexport.impl;

import stroom.security.shared.User;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.UserType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ContentPackImportConfig extends AbstractConfig implements IsStroomConfig {

    private final boolean enabled;
    private final String importDirectory;
    private final String importAsSubjectId;
    private final UserType importAsType;

    public ContentPackImportConfig() {
        enabled = false;
        importDirectory = "content_pack_import";
        importAsSubjectId = User.ADMINISTRATORS_GROUP_SUBJECT_ID;
        importAsType = UserType.GROUP;
    }

    @JsonCreator
    public ContentPackImportConfig(@JsonProperty("enabled") final boolean enabled,
                                   @JsonProperty("importDirectory") final String importDirectory,
                                   @JsonProperty("importAsSubjectId") final String importAsSubjectId,
                                   @JsonProperty("importAsType") final UserType importAsType) {
        this.enabled = enabled;
        this.importDirectory = importDirectory;
        this.importAsSubjectId = importAsSubjectId;
        this.importAsType = importAsType;
    }

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "If true any content packs found in 'importDirectory' will be imported " +
            "into Stroom. Only intended for use on new Stroom instances to reduce the risk of " +
            "overwriting existing entities.")
    public boolean isEnabled() {
        return enabled;
    }

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "When stroom starts, if 'enabled' is set to true, it will attempt to import content " +
            "packs from this directory. If the value is null or the directory does not exist it will be ignored." +
            "If the value is a relative path then it will be treated as being relative to stroom.path.home.")
    public String getImportDirectory() {
        return importDirectory;
    }

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "The unique identifier for the user/group that the content import will run as. " +
            "In the case of an Open ID Connect user" +
            "this would be the claim value that uniquely identifies the user on the IDP (often 'sub' or 'oid'). " +
            "These values are often UUIDs and thus not pretty to look at for an admin. " +
            "For the internal IDP this would likely be a more human friendly username. " +
            "Currently import can only imported as a user and not a group.")
    public String getImportAsSubjectId() {
        return importAsSubjectId;
    }

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("The type of the entity represented by importAsSubjectId, i.g. 'USER' or 'GROUP'")
    public UserType getImportAsType() {
        return importAsType;
    }

    @Override
    public String toString() {
        return "ContentPackImportConfig{" +
               "enabled=" + enabled +
               ", importDirectory='" + importDirectory + '\'' +
               ", importAsSubjectId='" + importAsSubjectId + '\'' +
//                ", importAsType=" + importAsType +
               '}';
    }
}
