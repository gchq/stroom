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

import stroom.dispatch.client.FakeRestFactory;
import stroom.document.client.component.NameEntry;
import stroom.security.shared.UserResource;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestGroupNameController {

    private NameEntry view;
    private final FakeRestFactory restFactory = new FakeRestFactory();
    private GroupNameController controller;

    @BeforeEach
    void setUp() {
        // The REST resource is made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
        view = Mockito.mock(NameEntry.class);
        controller = new GroupNameController(new SimpleEventBus(), restFactory).attach(view);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void create_noName(final String name) {
        // Regression test: OK with no name asked the server to create a group with none
        Mockito.when(view.getName()).thenReturn(name);
        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);

        controller.create(user -> {
        }, okPressed, Mockito.mock(TaskMonitorFactory.class));

        Mockito.verify(view).setNameInvalid("You must provide a name for the group");
        Mockito.verify(okPressed).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
        assertThat(restFactory.getRequests())
                .isEmpty();
    }

    @Test
    void create_name() {
        Mockito.when(view.getName()).thenReturn(" Admins ");
        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);

        controller.create(user -> {
        }, okPressed, Mockito.mock(TaskMonitorFactory.class));

        // The group is created with the name trimmed
        final UserResource userResource = Mockito.mock(UserResource.class);
        restFactory.getLastRequest().callOn(userResource);
        Mockito.verify(userResource).createGroup("Admins");
        Mockito.verify(view, Mockito.never()).setNameInvalid(Mockito.anyString());
        Mockito.verify(okPressed, Mockito.never()).reset();
    }
}
