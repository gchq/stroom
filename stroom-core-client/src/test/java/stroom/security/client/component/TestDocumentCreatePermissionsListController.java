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
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeGroup;
import stroom.explorer.shared.ExplorerConstants;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.svg.shared.SvgImage;

import com.google.gwt.junit.GWTMockUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentCreatePermissionsListController {

    private static final List<DocumentType> TYPES = List.of(
            new DocumentType(DocumentTypeGroup.STRUCTURE, "Dictionary", "Dictionary", SvgImage.DOCUMENT_DICTIONARY),
            new DocumentType(DocumentTypeGroup.DATA_PROCESSING, "Feed", "Feed", SvgImage.DOCUMENT_FEED));

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
        // Nothing is ticked before a report is given
        assertThat(DocumentCreatePermissionsListController.permissionState(null, Set.of("Feed"), "Feed"))
                .isEqualTo(TickBoxState.UNTICK);
    }

    @Test
    void testExplicit() {
        final DocumentUserPermissionsReport report = report(null, Map.of(), Map.of());
        assertThat(DocumentCreatePermissionsListController.permissionState(report, Set.of("Feed"), "Feed"))
                .isEqualTo(TickBoxState.TICK);
        assertThat(DocumentCreatePermissionsListController.permissionState(report, Set.of("Feed"), "Dictionary"))
                .isEqualTo(TickBoxState.UNTICK);
    }

    @Test
    void testImplied() {
        final DocumentUserPermissionsReport report = report(null, Map.of(), Map.of());
        // By 'Create All'
        assertThat(DocumentCreatePermissionsListController.permissionState(
                report, Set.of(ExplorerConstants.ALL_CREATE_PERMISSIONS), "Feed"))
                .isEqualTo(TickBoxState.HALF_TICK);
        // By ownership, explicit or inherited
        assertThat(DocumentCreatePermissionsListController.permissionState(
                report(DocumentPermission.OWNER, Map.of(), Map.of()), Set.of(), "Feed"))
                .isEqualTo(TickBoxState.HALF_TICK);
        assertThat(DocumentCreatePermissionsListController.permissionState(
                report(null, Map.of(DocumentPermission.OWNER, List.of("/System")), Map.of()), Set.of(), "Feed"))
                .isEqualTo(TickBoxState.HALF_TICK);
        // Inherited for the type alone
        final DocumentUserPermissionsReport inherited = report(null, Map.of(), Map.of("Feed", List.of("/System")));
        assertThat(DocumentCreatePermissionsListController.permissionState(inherited, Set.of(), "Feed"))
                .isEqualTo(TickBoxState.HALF_TICK);
        assertThat(DocumentCreatePermissionsListController.permissionState(inherited, Set.of(), "Dictionary"))
                .isEqualTo(TickBoxState.UNTICK);
    }

    @Test
    void testAllState() {
        final DocumentUserPermissionsReport report = report(null, Map.of(), Map.of());
        assertThat(DocumentCreatePermissionsListController.allState(report, Set.of(), TYPES))
                .isEqualTo(TickBoxState.UNTICK);
        assertThat(DocumentCreatePermissionsListController.allState(report, Set.of("Feed"), TYPES))
                .isEqualTo(TickBoxState.HALF_TICK);
        assertThat(DocumentCreatePermissionsListController.allState(
                report, Set.of(ExplorerConstants.ALL_CREATE_PERMISSIONS), TYPES))
                .isEqualTo(TickBoxState.TICK);
        // No report or set yet
        assertThat(DocumentCreatePermissionsListController.allState(null, null, TYPES))
                .isEqualTo(TickBoxState.UNTICK);
    }

    private static DocumentUserPermissionsReport report(
            final DocumentPermission explicit,
            final Map<DocumentPermission, List<String>> inheritedPermissionPaths,
            final Map<String, List<String>> inheritedCreatePermissionPaths) {
        return new DocumentUserPermissionsReport(explicit, Collections.emptySet(), inheritedPermissionPaths,
                inheritedCreatePermissionPaths);
    }
}
