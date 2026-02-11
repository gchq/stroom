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

package stroom.util.shared;

public abstract class AbstractHasAuditInfoBuilder<T, B extends AbstractHasAuditInfoBuilder<T, ?>>
        extends AbstractBuilder<T, B> {

    protected Long createTimeMs;
    protected String createUser;
    protected Long updateTimeMs;
    protected String updateUser;

    public AbstractHasAuditInfoBuilder() {
    }

    public AbstractHasAuditInfoBuilder(final HasAuditInfoGetters hasAuditInfoGetters) {
        this.createTimeMs = hasAuditInfoGetters.getCreateTimeMs();
        this.createUser = hasAuditInfoGetters.getCreateUser();
        this.updateTimeMs = hasAuditInfoGetters.getUpdateTimeMs();
        this.updateUser = hasAuditInfoGetters.getUpdateUser();
    }

    public B createTimeMs(final Long createTimeMs) {
        this.createTimeMs = createTimeMs;
        return self();
    }

    public B createUser(final String createUser) {
        this.createUser = createUser;
        return self();
    }

    public B updateTimeMs(final Long updateTimeMs) {
        this.updateTimeMs = updateTimeMs;
        return self();
    }

    public B updateUser(final String updateUser) {
        this.updateUser = updateUser;
        return self();
    }
}
