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

package stroom.dashboard.client.component;

import stroom.dashboard.shared.ComponentSelectionHandler;

import com.google.gwt.junit.GWTMockUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestSelectionHandlersController {

    @BeforeEach
    void setUp() {
        // The controller's class refers to GWT widgets, which only load with GWT's bridge disarmed
        GWTMockUtilities.disarm();
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void aDuplicateHasAnIdOfItsOwn() {
        // Before, Duplicate kept the original's id
        final ComponentSelectionHandler original = ComponentSelectionHandler.builder()
                .id("abcde")
                .componentId("table-1")
                .enabled(true)
                .build();

        final ComponentSelectionHandler copy = SelectionHandlersController.duplicate(original);

        assertThat(copy.getId()).isNotNull().isNotEqualTo("abcde");
        assertThat(copy.getComponentId()).isEqualTo("table-1");
        assertThat(copy.isEnabled()).isTrue();
    }
}
