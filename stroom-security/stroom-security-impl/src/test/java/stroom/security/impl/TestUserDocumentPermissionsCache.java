/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.security.impl;

import stroom.cache.api.LoadingStroomCache;
import stroom.cache.impl.CacheManagerImpl;
import stroom.docref.DocRef;
import stroom.security.impl.event.PermissionChangeEvent;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestUserDocumentPermissionsCache {

    private static final String CACHE_NAME = "User Document Permissions Cache";
    private static final int BATCH_SIZE = UserDocumentPermissionsCache.BATCH_SIZE;
    private static final int USER_COUNT = BATCH_SIZE * 2 + 1;
    private static final DocRef DOC_REF = new DocRef("test", "doc");

    @Mock
    private DocumentPermissionDao documentPermissionDao;

    private CacheManagerImpl cacheManager;
    private UserDocumentPermissionsCache userDocumentPermissionsCache;

    /// Creates a fresh real cache manager and cache for each test.
    @BeforeEach
    void setUp() {
        cacheManager = new CacheManagerImpl();
        userDocumentPermissionsCache = new UserDocumentPermissionsCache(
                cacheManager,
                () -> documentPermissionDao,
                AuthorisationConfig::new);
    }

    /// Clears the real cache manager after each test.
    @AfterEach
    void tearDown() {
        cacheManager.close();
    }

    /// A change for one user invalidates only that user's cached permissions.
    @Test
    void onChangeWithUserRefInvalidatesOnlyThatUser() {
        when(documentPermissionDao.getPermissionsForUser(anyString()))
                .thenReturn(UserDocumentPermissions.EMPTY);

        final UserRef userRef = UserRef.forUserUuid("user-1");
        final UserRef otherUserRef = UserRef.forUserUuid("user-2");
        assertThat(userDocumentPermissionsCache.hasDocumentPermission(
                userRef, DOC_REF, DocumentPermission.VIEW)).isFalse();
        assertThat(userDocumentPermissionsCache.hasDocumentPermission(
                otherUserRef, DOC_REF, DocumentPermission.VIEW)).isFalse();

        clearInvocations(documentPermissionDao);

        userDocumentPermissionsCache.onChange(new PermissionChangeEvent(userRef, DOC_REF));

        assertThat(getLoadingCache().keySet())
                .contains(otherUserRef)
                .doesNotContain(userRef);
        verify(documentPermissionDao, never()).getPermissionsForUsers(anySet());

        assertThat(userDocumentPermissionsCache.hasDocumentPermission(
                userRef, DOC_REF, DocumentPermission.VIEW)).isFalse();
        verify(documentPermissionDao).getPermissionsForUser(userRef.getUuid());
    }

    /// A change without a user does not call the bulk DAO when the cache is empty.
    @Test
    void onChangeWithoutUserRefDoesNothingWhenCacheIsEmpty() {
        userDocumentPermissionsCache.onChange(new PermissionChangeEvent(null, DOC_REF));

        verifyNoInteractions(documentPermissionDao);
        assertThat(getLoadingCache().keySet()).isEmpty();
    }

    /// A change without a user reloads all cached users in batches of no more than 200 users.
    @Test
    void onChangeWithoutUserRefReloadsLargeNumbersOfUsersInBatches() {
        when(documentPermissionDao.getPermissionsForUser(anyString()))
                .thenReturn(UserDocumentPermissions.EMPTY);

        final Set<UserRef> userRefs = createUserRefs(USER_COUNT);
        userRefs.forEach(userRef -> assertThat(userDocumentPermissionsCache.hasDocumentPermission(
                userRef, DOC_REF, DocumentPermission.VIEW)).isFalse());
        assertThat(getLoadingCache().keySet())
                .containsExactlyInAnyOrderElementsOf(userRefs);

        final List<Set<UserRef>> requestedBatches = new ArrayList<>();
        when(documentPermissionDao.getPermissionsForUsers(anySet()))
                .thenAnswer(invocation -> {
                    final Set<UserRef> requestedUserRefs = new HashSet<>(invocation.getArgument(0));
                    requestedBatches.add(requestedUserRefs);
                    return permissionsFor(requestedUserRefs);
                });
        clearInvocations(documentPermissionDao);

        userDocumentPermissionsCache.onChange(new PermissionChangeEvent(null, DOC_REF));

        assertThat(requestedBatches)
                .extracting(Set::size)
                .containsExactlyInAnyOrder(BATCH_SIZE, BATCH_SIZE, 1);
        assertThat(requestedBatches.stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(userRefs);
        assertThat(getLoadingCache().keySet())
                .containsExactlyInAnyOrderElementsOf(userRefs);
        verify(documentPermissionDao, never()).getPermissionsForUser(anyString());
        verify(documentPermissionDao, times(3)).getPermissionsForUsers(anySet());
    }

    private LoadingStroomCache<UserRef, UserDocumentPermissions> getLoadingCache() {
        return cacheManager.getLoadingCache(CACHE_NAME);
    }

    private Set<UserRef> createUserRefs(final int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> UserRef.forUserUuid("user-" + index))
                .collect(Collectors.toSet());
    }

    private Map<UserRef, UserDocumentPermissions> permissionsFor(final Set<UserRef> userRefs) {
        final Map<UserRef, UserDocumentPermissions> permissions = new HashMap<>();
        userRefs.forEach(userRef -> permissions.put(userRef, UserDocumentPermissions.EMPTY));
        return permissions;
    }
}
