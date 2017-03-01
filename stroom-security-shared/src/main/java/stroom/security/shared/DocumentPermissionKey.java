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

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

import java.io.Serializable;

public class DocumentPermissionKey implements Serializable, Comparable<DocumentPermissionKey> {
    private static final long serialVersionUID = 2536752322307664050L;

    private String docType;
    private String docId;
    private String permission;

    public DocumentPermissionKey() {
        // Default constructor necessary for GWT serialisation.
    }

    public DocumentPermissionKey(final String docType, final String docId, final String permission) {
        this.docType = docType;
        this.docId = docId;
        this.permission = permission;
    }

    public String getDocType() {
        return docType;
    }

    public String getDocId() {
        return docId;
    }

    public String getPermission() {
        return permission;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder hashCodeBuilder = new HashCodeBuilder();
        hashCodeBuilder.append(docType);
        hashCodeBuilder.append(docId);
        hashCodeBuilder.append(permission);
        return hashCodeBuilder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof DocumentPermissionKey)) {
            return false;
        }

        final DocumentPermissionKey keyByName = (DocumentPermissionKey) o;
        final EqualsBuilder equalsBuilder = new EqualsBuilder();
        equalsBuilder.append(docId, keyByName.docId);
        equalsBuilder.append(docType, keyByName.docType);
        equalsBuilder.append(permission, keyByName.permission);
        return equalsBuilder.isEquals();
    }

    @Override
    public String toString() {
        return docType + "-" + docId + "-" + permission;
    }

    @Override
    public int compareTo(final DocumentPermissionKey documentPermissionKey) {
        return toString().compareTo(documentPermissionKey.toString());
    }
}
