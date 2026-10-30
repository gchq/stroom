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

package stroom.security.client.component;

import stroom.cell.tickbox.client.TickBoxState;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppUserPermissionsReport;

import com.google.gwt.junit.GWTMockUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestAppPermissionsEditorController {

    @BeforeEach
    void setUp() {
        // Loading a controller loads GWT classes that GWT.create in a browser only
        GWTMockUtilities.disarm();
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void testNoReport() {
        assertThat(AppPermissionsEditorController.tickBoxState(null, AppPermission.MANAGE_USERS_PERMISSION))
                .isEqualTo(TickBoxState.UNTICK);
    }

    @Test
    void testExplicitAndInherited() {
        final AppUserPermissionsReport report = new AppUserPermissionsReport(
                Set.of(AppPermission.MANAGE_USERS_PERMISSION),
                Map.of(AppPermission.MANAGE_API_KEYS, List.of("Group A")));
        assertThat(AppPermissionsEditorController.tickBoxState(report, AppPermission.MANAGE_USERS_PERMISSION))
                .isEqualTo(TickBoxState.TICK);
        assertThat(AppPermissionsEditorController.tickBoxState(report, AppPermission.MANAGE_API_KEYS))
                .isEqualTo(TickBoxState.HALF_TICK);
        assertThat(AppPermissionsEditorController.tickBoxState(report, AppPermission.ADMINISTRATOR))
                .isEqualTo(TickBoxState.UNTICK);
    }

    @Test
    void testImpliedByAdministrator() {
        // Held explicitly
        final AppUserPermissionsReport admin = new AppUserPermissionsReport(
                Set.of(AppPermission.ADMINISTRATOR), Map.of());
        assertThat(AppPermissionsEditorController.tickBoxState(admin, AppPermission.MANAGE_USERS_PERMISSION))
                .isEqualTo(TickBoxState.HALF_TICK);
        assertThat(AppPermissionsEditorController.tickBoxState(admin, AppPermission.ADMINISTRATOR))
                .isEqualTo(TickBoxState.TICK);

        // Inherited
        final AppUserPermissionsReport inheritedAdmin = new AppUserPermissionsReport(
                Set.of(), Map.of(AppPermission.ADMINISTRATOR, List.of("Admins")));
        assertThat(AppPermissionsEditorController.tickBoxState(inheritedAdmin, AppPermission.MANAGE_USERS_PERMISSION))
                .isEqualTo(TickBoxState.HALF_TICK);
        assertThat(AppPermissionsEditorController.tickBoxState(inheritedAdmin, AppPermission.ADMINISTRATOR))
                .isEqualTo(TickBoxState.HALF_TICK);
    }
}
