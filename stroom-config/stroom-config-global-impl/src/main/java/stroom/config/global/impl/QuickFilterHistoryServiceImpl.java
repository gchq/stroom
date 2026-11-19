/*
 * Copyright 2026 Crown Copyright
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

package stroom.config.global.impl;

import stroom.quickfilter.shared.QuickFilterHistoryKey;
import stroom.quickfilter.shared.QuickFilterHistoryResource;
import stroom.quickfilter.shared.QuickFilterHistoryService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.ui.config.shared.UiConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Objects;

public class QuickFilterHistoryServiceImpl implements QuickFilterHistoryService {

    private final QuickFilterHistoryDao dao;
    private final SecurityContext securityContext;
    private final Provider<UiConfig> uiConfigProvider;

    @Inject
    QuickFilterHistoryServiceImpl(final QuickFilterHistoryDao dao,
                                  final SecurityContext securityContext,
                                  final Provider<UiConfig> uiConfigProvider) {
        this.dao = dao;
        this.securityContext = securityContext;
        this.uiConfigProvider = uiConfigProvider;
    }

    @Override
    public List<String> fetch(final QuickFilterHistoryKey key) {
        Objects.requireNonNull(key);
        return securityContext.secureResult(() ->
                dao.fetch(securityContext.getUserRef().getUuid(), key, historySize()));
    }

    @Override
    public void recordUse(final QuickFilterHistoryKey key, final String filterText) {
        Objects.requireNonNull(key);
        // Trimmed so that "abc" and "abc " are one entry, and so that the leading/trailing
        // whitespace the parser ignores does not become a visible difference in the list.
        final String trimmed = NullSafe.trim(filterText);
        if (NullSafe.isBlankString(trimmed)
            || trimmed.length() > QuickFilterHistoryResource.MAX_FILTER_TEXT_LENGTH) {
            return;
        }
        securityContext.secure(() ->
                dao.recordUse(
                        securityContext.getUserRef().getUuid(),
                        key,
                        trimmed,
                        System.currentTimeMillis(),
                        historySize()));
    }

    @Override
    public void clear(final QuickFilterHistoryKey key) {
        Objects.requireNonNull(key);
        securityContext.secure(() ->
                dao.clear(securityContext.getUserRef().getUuid(), key));
    }

    @Override
    public int delete(final UserRef userRef) {
        Objects.requireNonNull(userRef);
        // Same rule as user preferences: your own, or anyone's if you can manage users.
        return securityContext.secureResult(() -> {
            if (securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
                || securityContext.isCurrentUser(userRef)) {
                return dao.deleteAll(userRef.getUuid());
            } else {
                throw new PermissionException(securityContext.getUserRef(),
                        "You must be the owner of the quick filter history to delete it, or hold "
                        + AppPermission.MANAGE_USERS_PERMISSION.getDisplayValue() + " permission");
            }
        });
    }

    private int historySize() {
        return Math.max(1, uiConfigProvider.get().getQuickFilterHistorySize());
    }
}
