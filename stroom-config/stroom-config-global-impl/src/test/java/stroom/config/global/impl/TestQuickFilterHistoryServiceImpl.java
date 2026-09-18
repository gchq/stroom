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
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.ui.config.shared.UiConfig;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestQuickFilterHistoryServiceImpl {

    private static final UserRef ME = UserRef.builder().uuid("me").subjectId("me").build();
    private static final UserRef SOMEONE_ELSE = UserRef.builder().uuid("them").subjectId("them").build();
    private static final QuickFilterHistoryKey KEY = new QuickFilterHistoryKey("dependencies", null);

    @Mock
    private QuickFilterHistoryDao dao;
    @Mock
    private SecurityContext securityContext;

    private QuickFilterHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        when(securityContext.getUserRef()).thenReturn(ME);
        when(securityContext.isCurrentUser(ME)).thenReturn(true);
        when(securityContext.secureResult(any(Supplier.class)))
                .thenAnswer(inv -> inv.<Supplier<?>>getArgument(0).get());
        org.mockito.Mockito.doAnswer(inv -> {
            inv.<Runnable>getArgument(0).run();
            return null;
        }).when(securityContext).secure(any(Runnable.class));

        final UiConfig uiConfig = new UiConfig();
        service = new QuickFilterHistoryServiceImpl(dao, securityContext, () -> uiConfig);
    }

    @Test
    void testRecordUseTrimsAndPassesTheCallersUuid() {
        service.recordUse(KEY, "  status:Missing  ");

        verify(dao).recordUse(eq("me"), eq(KEY), eq("status:Missing"), anyLong(), eq(20));
    }

    @Test
    void testBlankIsNotRecorded() {
        service.recordUse(KEY, "   ");
        service.recordUse(KEY, null);

        verify(dao, never()).recordUse(any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void testOverlongIsNotRecorded() {
        final String justRight = "x".repeat(QuickFilterHistoryResource.MAX_FILTER_TEXT_LENGTH);
        final String tooLong = justRight + "x";

        service.recordUse(KEY, justRight);
        service.recordUse(KEY, tooLong);

        verify(dao).recordUse(eq("me"), eq(KEY), eq(justRight), anyLong(), anyInt());
        verify(dao, never()).recordUse(any(), any(), eq(tooLong), anyLong(), anyInt());
    }

    @Test
    void testFetchIsForTheCallerOnly() {
        service.fetch(KEY);

        verify(dao).fetch("me", KEY, 20);
    }

    @Test
    void testDeleteOwnHistoryIsAllowed() {
        service.delete(ME);

        verify(dao).deleteAll("me");
    }

    @Test
    void testDeleteAnotherUsersHistoryNeedsManageUsers() {
        when(securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(SOMEONE_ELSE))
                .isInstanceOf(PermissionException.class);
        verify(dao, never()).deleteAll(any());

        when(securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)).thenReturn(true);
        service.delete(SOMEONE_ELSE);
        verify(dao).deleteAll("them");
    }

    @Test
    void testHistorySizeIsAtLeastOne() {
        final UiConfig zero = new UiConfig(null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0);
        service = new QuickFilterHistoryServiceImpl(dao, securityContext, () -> zero);

        service.fetch(KEY);

        verify(dao).fetch("me", KEY, 1);
        assertThat(zero.getQuickFilterHistorySize()).isZero();
    }
}
