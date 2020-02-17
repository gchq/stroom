/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CheckDocumentPermissionRequest {
    @JsonProperty
    private final String documentType;
    @JsonProperty
    private final String documentId;
    @JsonProperty
    private final String permission;

    @JsonCreator
    public CheckDocumentPermissionRequest(@JsonProperty("documentType") final String documentType,
                                          @JsonProperty("documentId") final String documentId,
                                          @JsonProperty("permission") final String permission) {
        this.documentType = documentType;
        this.documentId = documentId;
        this.permission = permission;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getPermission() {
        return permission;
    }
}
