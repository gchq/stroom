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

package stroom.analytics.client.component;

import stroom.node.client.NodeClient;
import stroom.schedule.client.component.ScheduleBoxController;
import stroom.security.client.component.UserRefSelectionController;
import stroom.security.shared.FindUserContext;
import stroom.widget.datepicker.client.component.DateTimeBoxController;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;

class TestBatchExecutionScheduleEditorController {

    @BeforeEach
    void setUp() {
        GWTMockUtilities.disarm();
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void runAsUserOffersOnlyUsersYouCanRunAs() {
        final UserRefSelectionController runAsUser = Mockito.mock(UserRefSelectionController.class);
        Mockito.when(runAsUser.attach(any())).thenReturn(runAsUser);
        final ScheduleBoxController scheduleBox = Mockito.mock(ScheduleBoxController.class);
        Mockito.when(scheduleBox.attach(any())).thenReturn(scheduleBox);
        final DateTimeBoxController dateTimeBox = Mockito.mock(DateTimeBoxController.class);
        Mockito.when(dateTimeBox.attach(any())).thenReturn(dateTimeBox);

        new BatchExecutionScheduleEditorController(
                new SimpleEventBus(),
                Mockito.mock(BatchExecutionScheduleEditor.class),
                Mockito.mock(NodeClient.class),
                () -> scheduleBox,
                () -> dateTimeBox,
                () -> runAsUser);

        // As the single schedule editor's picker does; before, it listed every user, and the
        // server refused one you can't run as only when the change was applied
        Mockito.verify(runAsUser).setContext(FindUserContext.RUN_AS);
    }
}
