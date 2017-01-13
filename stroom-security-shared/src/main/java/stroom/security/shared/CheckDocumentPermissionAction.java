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

import stroom.entity.shared.Action;
import stroom.util.shared.SharedBoolean;

public class CheckDocumentPermissionAction extends Action<SharedBoolean> {
    private static final long serialVersionUID = -6740095230475597845L;

    private String documentType;
    private String documentId;
    private String permission;

    public CheckDocumentPermissionAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public CheckDocumentPermissionAction(final String documentType, final String documentId, final String permission) {
        this.documentType = documentType;
        this.documentId = documentId;
        this.permission = permission;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(final String documentType) {
        this.documentType = documentType;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(final String documentId) {
        this.documentId = documentId;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(final String permission) {
        this.permission = permission;
    }

    @Override
    public String getTaskName() {
        return "Check Document Permission";
    }
}
