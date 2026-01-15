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

package stroom.security.shared;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestAppPermissionSet {

    @Test
    void testSingleton() {
        final AppPermissionSet set1 = AppPermissionSet.of(AppPermission.STEPPING_PERMISSION);
        assertThat(set1.asSet())
                .isEqualTo(Set.of(AppPermission.STEPPING_PERMISSION));
        assertThat(set1.isEmpty())
                .isFalse();
        assertThat(set1.size())
                .isEqualTo(1);
        assertThat(set1.isAllOf())
                .isTrue();
        assertThat(set1.isAtLeastOneOf())
                .isTrue();
        assertThat(set1.contains(AppPermission.STEPPING_PERMISSION))
                .isTrue();

        assertThat(set1.check(Set.of()))
                .isFalse();
        assertThat(set1.check(Set.of(AppPermission.VERIFY_API_KEY)))
                .isFalse();
        assertThat(set1.check(Set.of(AppPermission.STEPPING_PERMISSION)))
                .isTrue();
        assertThat(set1.check(Set.of(AppPermission.STEPPING_PERMISSION, AppPermission.VIEW_DATA_PERMISSION)))
                .isTrue();
        assertThat(set1.check(Set.of(
                AppPermission.STEPPING_PERMISSION,
                AppPermission.VIEW_DATA_PERMISSION,
                AppPermission.STROOM_PROXY)))
                .isTrue();
    }

    @Test
    void testAllOf() {
        final AppPermissionSet set1 = AppPermissionSet.allOf(
                AppPermission.VERIFY_API_KEY,
                AppPermission.STROOM_PROXY);
        final AppPermissionSet set2 = AppPermissionSet.allOf(Set.of(
                AppPermission.VERIFY_API_KEY,
                AppPermission.STROOM_PROXY));
        final AppPermissionSet set3 = AppPermissionSet.oneOf(Set.of(
                AppPermission.VERIFY_API_KEY,
                AppPermission.STROOM_PROXY));

        assertThat(set2)
                .isEqualTo(set1);
        assertThat(set3)
                .isNotEqualTo(set1);

        assertThat(set1.check(Set.of()))
                .isFalse();
        assertThat(set1.check(Set.of(AppPermission.VERIFY_API_KEY)))
                .isFalse();
        assertThat(set1.check(Set.of(AppPermission.STROOM_PROXY)))
                .isFalse();
        assertThat(set1.check(Set.of(AppPermission.VERIFY_API_KEY, AppPermission.VIEW_DATA_PERMISSION)))
                .isFalse();
        assertThat(set1.check(Set.of(
                AppPermission.VERIFY_API_KEY,
                AppPermission.VIEW_DATA_PERMISSION,
                AppPermission.STROOM_PROXY)))
                .isTrue();
    }

    @Test
    void testOneOf() {
        final AppPermissionSet set1 = AppPermissionSet.oneOf(
                AppPermission.VERIFY_API_KEY,
                AppPermission.STROOM_PROXY);
        final AppPermissionSet set2 = AppPermissionSet.oneOf(Set.of(
                AppPermission.VERIFY_API_KEY,
                AppPermission.STROOM_PROXY));
        final AppPermissionSet set3 = AppPermissionSet.allOf(Set.of(
                AppPermission.VERIFY_API_KEY,
                AppPermission.STROOM_PROXY));

        assertThat(set2)
                .isEqualTo(set1);
        assertThat(set3)
                .isNotEqualTo(set1);

        assertThat(set1.check(Set.of()))
                .isFalse();
        assertThat(set1.check(Set.of(AppPermission.VERIFY_API_KEY)))
                .isTrue();
        assertThat(set1.check(Set.of(AppPermission.STROOM_PROXY)))
                .isTrue();
        assertThat(set1.check(Set.of(AppPermission.VERIFY_API_KEY, AppPermission.VIEW_DATA_PERMISSION)))
                .isTrue();
        assertThat(set1.check(Set.of(
                AppPermission.VERIFY_API_KEY,
                AppPermission.VIEW_DATA_PERMISSION,
                AppPermission.STROOM_PROXY)))
                .isTrue();
    }

    @Test
    void testSerialisation_empty() {
        final AppPermissionSet permSet = AppPermissionSet.empty();
        TestUtil.testSerialisation(permSet, AppPermissionSet.class);
    }

    @Test
    void testSerialisation_single() {
        final AppPermissionSet permSet = AppPermission.VIEW_DATA_PERMISSION.asAppPermissionSet();
        TestUtil.testSerialisation(permSet, AppPermissionSet.class);
    }

    @Test
    void testSerialisation_multi_allOf() {
        final AppPermissionSet permSet = AppPermissionSet.allOf(
                AppPermission.VERIFY_API_KEY,
                AppPermission.ADMINISTRATOR,
                AppPermission.MANAGE_USERS_PERMISSION);
        TestUtil.testSerialisation(permSet, AppPermissionSet.class);
    }

    @Test
    void testSerialisation_multi_oneOf() {
        final AppPermissionSet permSet = AppPermissionSet.oneOf(
                AppPermission.VERIFY_API_KEY,
                AppPermission.ADMINISTRATOR,
                AppPermission.MANAGE_USERS_PERMISSION);
        TestUtil.testSerialisation(permSet, AppPermissionSet.class);
    }
}
