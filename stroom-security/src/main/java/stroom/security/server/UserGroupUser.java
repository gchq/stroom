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
import java.util.UUID;

/**
 * An entity to group users together and apply the same permissions to multiple
 * users.
 */
@Entity
@Table(name = "USR_GRP_USR", uniqueConstraints = @UniqueConstraint(columnNames = { UserGroupUser.GROUP_UUID,
        UserGroupUser.USER_UUID }) )
public class UserGroupUser extends BaseEntityBig {
    private static final long serialVersionUID = 3562407919517508053L;

    public static final String TABLE_NAME = SQLNameConstants.USER + SEP + SQLNameConstants.GROUP + SEP
            + SQLNameConstants.USER;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;

    public static final String GROUP_UUID = SQLNameConstants.GROUP + SEP + SQLNameConstants.UUID;
    public static final String USER_UUID = SQLNameConstants.USER + SEP + SQLNameConstants.UUID;

    public static final String ENTITY_TYPE = "UserGroupUser";

    private String userGroupUuid;
    private String userUuid;

    public UserGroupUser() {
        // Default constructor necessary for GWT serialisation.
    }

    public UserGroupUser(final String userGroupUuid, final String userUuid) {
        this.userGroupUuid = userGroupUuid;
        this.userUuid = userUuid;
    }

    @Column(name = GROUP_UUID, nullable = false)
    public String getGroupUuid() {
        return userGroupUuid;
    }

    public void setGroupUuid(final String userGroupUuid) {
        this.userGroupUuid = userGroupUuid;
    }

    @Column(name = USER_UUID, nullable = false)
    public String getUserUuid() {
        return userUuid;
    }

    public void setUserUuid(final String userUuid) {
        this.userUuid = userUuid;
    }

    @Transient
    @Override
    public String getType() {
        return ENTITY_TYPE;
    }
}
