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

package stroom.security.server;

import stroom.entity.shared.BaseEntityBig;
import stroom.entity.shared.SQLNameConstants;
import stroom.security.shared.DocumentPermissionKey;
import stroom.security.shared.User;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

@Entity
@Table(name = "DOC_PERM", uniqueConstraints = @UniqueConstraint(columnNames = { DocumentPermission.USER_UUID,
        DocumentPermission.DOC_TYPE, DocumentPermission.DOC_UUID, DocumentPermission.PERMISSION }) )
public class DocumentPermission extends BaseEntityBig {
    public static final String TABLE_NAME = SQLNameConstants.DOCUMENT + SEP + SQLNameConstants.PERMISSION;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String USER_UUID = User.TABLE_NAME + SEP + User.UUID;
    public static final String DOC_TYPE = SQLNameConstants.DOCUMENT + SEP + SQLNameConstants.TYPE;
    public static final String DOC_UUID = SQLNameConstants.DOCUMENT + SEP + SQLNameConstants.UUID;
    public static final String PERMISSION = SQLNameConstants.PERMISSION;
    public static final String ENTITY_TYPE = "DocumentPermission";
    private static final long serialVersionUID = 3562407919517508053L;
    private String userUuid;
    private String docType;
    private String docUuid;
    private String permission;

    public DocumentPermission() {
    }

    public DocumentPermission(final String userUuid, final String docType, final String docUuid, final String permission) {
        this.userUuid = userUuid;
        this.docType = docType;
        this.docUuid = docUuid;
        this.permission = permission;
    }

    @Column(name = USER_UUID, columnDefinition = NORMAL_KEY_DEF, nullable = false)
    public String getUserUuid() {
        return userUuid;
    }

    public void setUserUuid(final String userUuid) {
        this.userUuid = userUuid;
    }

    @Column(name = DOC_TYPE, nullable = false)
    public String getDocType() {
        return docType;
    }

    public void setDocType(final String docType) {
        this.docType = docType;
    }

    @Column(name = DOC_UUID, nullable = false)
    public String getDocUuid() {
        return docUuid;
    }

    public void setDocUuid(final String docUuid) {
        this.docUuid = docUuid;
    }

    @Column(name = PERMISSION, nullable = false)
    public String getPermission() {
        return permission;
    }

    public void setPermission(final String permission) {
        this.permission = permission;
    }

    @Transient
    public DocumentPermissionKey toKey() {
        return new DocumentPermissionKey(docType, docUuid, permission);
    }

    @Transient
    @Override
    public String getType() {
        return ENTITY_TYPE;
    }
}
