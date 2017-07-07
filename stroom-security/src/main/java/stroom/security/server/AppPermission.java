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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

@Entity
@Table(name = "APP_PERM", uniqueConstraints = @UniqueConstraint(columnNames = {AppPermission.USER_UUID,
        AppPermission.PERMISSION}))
public class AppPermission extends BaseEntityBig {
    public static final String TABLE_NAME = SQLNameConstants.APPLICATION + SEP + SQLNameConstants.PERMISSION;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String USER_UUID = User.TABLE_NAME + SEP + User.UUID;
    public static final String PERMISSION = Permission.FOREIGN_KEY;
    public static final String ENTITY_TYPE = "AppPermission";

    private static final long serialVersionUID = 3562407919517508053L;

    private String userUuid;
    private Permission permission;

    public AppPermission() {
    }

    public AppPermission(final String userUuid, final Permission permission) {
        this.userUuid = userUuid;
        this.permission = permission;
    }

    @Column(name = USER_UUID, columnDefinition = NORMAL_KEY_DEF, nullable = false)
    public String getUserUuid() {
        return userUuid;
    }

    public void setUserUuid(final String userUuid) {
        this.userUuid = userUuid;
    }

    @Column(name = PERMISSION, columnDefinition = NORMAL_KEY_DEF, nullable = false)
    public Permission getPermission() {
        return permission;
    }

    public void setPermission(final Permission permission) {
        this.permission = permission;
    }

    @Transient
    @Override
    public String getType() {
        return ENTITY_TYPE;
    }
}
