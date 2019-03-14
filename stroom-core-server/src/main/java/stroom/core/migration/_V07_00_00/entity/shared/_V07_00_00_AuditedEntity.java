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

package stroom.core.migration._V07_00_00.entity.shared;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class _V07_00_00_AuditedEntity extends _V07_00_00_BaseEntitySmall {
    public static final String CREATE_TIME = "CRT_MS";
    public static final String CREATE_USER = "CRT_USER";
    public static final String UPDATE_TIME = "UPD_MS";
    public static final String UPDATE_USER = "UPD_USER";
    public static final Set<String> AUDIT_FIELDS = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList("createTime", "updateTime", "createUser", "updateUser")));
    private static final long serialVersionUID = -2887373467654809485L;

    private Long createTime;
    private Long updateTime;
    private String createUser;
    private String updateUser;

    @Override
    public void clearPersistence() {
        super.clearPersistence();
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(final Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(final Long updateTime) {
        this.updateTime = updateTime;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }
}
