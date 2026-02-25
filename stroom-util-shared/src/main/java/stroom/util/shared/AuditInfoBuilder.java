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

public abstract class AuditInfoBuilder<T, B extends AuditInfoBuilder<T, ?>>
        extends AbstractBuilder<T, B>
        implements HasAuditInfoBuilder<T, B> {

    protected Long createTimeMs;
    protected String createUser;
    protected Long updateTimeMs;
    protected String updateUser;

    protected AuditInfoBuilder() {
    }

    @Override
    public final B createTimeMs(final Long createTimeMs) {
        this.createTimeMs = createTimeMs;
        return self();
    }

    @Override
    public final B createUser(final String createUser) {
        this.createUser = createUser;
        return self();
    }

    @Override
    public final B updateTimeMs(final Long updateTimeMs) {
        this.updateTimeMs = updateTimeMs;
        return self();
    }

    @Override
    public final B updateUser(final String updateUser) {
        this.updateUser = updateUser;
        return self();
    }

    @Override
    public final B stampAudit(final HasAuditableUserIdentity hasAuditableUserIdentity) {
        return stampAudit(hasAuditableUserIdentity.getUserIdentityForAudit());
    }

    @Override
    public final B stampAudit(final String user) {
        final long now = System.currentTimeMillis();
        if (createTimeMs == null) {
            this.createTimeMs = now;
        }
        if (createUser == null) {
            this.createUser = user;
        }
        updateTimeMs = now;
        updateUser = user;
        return self();
    }

    @Override
    public final B removeAudit() {
        this.createTimeMs = null;
        this.createUser = null;
        updateTimeMs = null;
        updateUser = null;
        return self();
    }
}
