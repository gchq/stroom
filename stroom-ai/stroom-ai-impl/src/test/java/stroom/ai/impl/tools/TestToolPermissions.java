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

package stroom.ai.impl.tools;

import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestToolPermissions {

    @Test
    void aToolWithNoChecksOfItsOwnHasNone() {
        assertThat(ToolPermissions.NONE.isNone()).isTrue();
        assertThat(ToolPermissions.NONE.appPermissions()).isEmpty();
        assertThat(ToolPermissions.NONE.documentPermissions()).isEmpty();
        assertThat(ToolPermissions.NONE.note()).isNull();
        // A note alone is not a check.
        assertThat(ToolPermissions.builder().note("Checked by the query service.").build().isNone()).isTrue();
    }

    @Test
    void theAppPermissionsAreThoseRequiredAndThoseThatUnlockParts() {
        final ToolPermissions permissions = ToolPermissions.builder()
                .requires(AppPermission.MANAGE_PROCESSORS_PERMISSION)
                .unlockedBy(AppPermission.VIEW_DATA_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.USE)
                .build();

        assertThat(permissions.isNone()).isFalse();
        assertThat(permissions.requiredAppPermissions()).containsExactly(AppPermission.MANAGE_PROCESSORS_PERMISSION);
        assertThat(permissions.unlockingAppPermissions()).containsExactly(AppPermission.VIEW_DATA_PERMISSION);
        assertThat(permissions.appPermissions()).containsExactlyInAnyOrder(
                AppPermission.MANAGE_PROCESSORS_PERMISSION, AppPermission.VIEW_DATA_PERMISSION);
        assertThat(permissions.documentPermissions())
                .containsExactlyInAnyOrder(DocumentPermission.VIEW, DocumentPermission.USE);
    }

    @Test
    void thePermissionsCantBeChanged() {
        final Set<AppPermission> given = EnumSet.of(AppPermission.MANAGE_TASKS_PERMISSION);
        final ToolPermissions permissions = new ToolPermissions(given, null, null, null);
        given.add(AppPermission.VIEW_DATA_PERMISSION);

        assertThat(permissions.requiredAppPermissions()).containsExactly(AppPermission.MANAGE_TASKS_PERMISSION);
        assertThat(permissions.unlockingAppPermissions()).isEmpty();
        assertThat(permissions.documentPermissions()).isEmpty();
        assertThatThrownBy(() -> permissions.requiredAppPermissions().add(AppPermission.VIEW_DATA_PERMISSION))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> permissions.appPermissions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aNoteMustBeGivenIfAsked() {
        assertThatThrownBy(() -> ToolPermissions.builder().note(null))
                .isInstanceOf(NullPointerException.class);
    }
}
