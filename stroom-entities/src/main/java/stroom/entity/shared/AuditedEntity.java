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

package stroom.entity.shared;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@MappedSuperclass
public abstract class AuditedEntity extends BaseEntitySmall {
    private static final long serialVersionUID = -2887373467654809485L;

    public static final String CREATE_TIME = "CRT_MS";
    public static final String CREATE_USER = "CRT_USER";
    public static final String UPDATE_TIME = "UPD_MS";
    public static final String UPDATE_USER = "UPD_USER";

    /**
     * The gets set depending on if we are running in the server side with a
     * spring context.
     */
    private static CurrentUserResolver currentUserResolver;

    public static void setCurrentUserResolver(final CurrentUserResolver acurrentUserResolver) {
        currentUserResolver = acurrentUserResolver;
    }

    private Long createTime;
    private Long updateTime;
    private String createUser;
    private String updateUser;

    public static final Set<String> AUDIT_FIELDS = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList("createTime", "updateTime", "createUser", "updateUser")));

    @Override
    public void clearPersistence() {
        super.clearPersistence();
    }

    @Column(name = CREATE_TIME, columnDefinition = BIGINT_UNSIGNED)
    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(final Long createTime) {
        this.createTime = createTime;
    }

    @Column(name = UPDATE_TIME, columnDefinition = BIGINT_UNSIGNED)
    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(final Long updateTime) {
        this.updateTime = updateTime;
    }

    @Column(name = CREATE_USER)
    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    @Column(name = UPDATE_USER)
    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }

    /**
     * @return the user who is logged in or NULL.
     */
    private static String getCurrentUser() {
        if (currentUserResolver != null) {
            return currentUserResolver.getCurrentUser();
        }
        return null;
    }

    /**
     * JPA hook.
     */
    @Override
    public void prePersist() {
        final long now = System.currentTimeMillis();
        setCreateTime(now);
        setCreateUser(getCurrentUser());
        setUpdateTime(now);
        setUpdateUser(getCurrentUser());
    }

    /**
     * JPA hook.
     */
    @Override
    public void preUpdate() {
        final long now = System.currentTimeMillis();
        setUpdateTime(now);
        setUpdateUser(getCurrentUser());
    }
}
