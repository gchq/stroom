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
import stroom.security.shared.UserResource;
import stroom.util.shared.UserDesc;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

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

class TestCreateExternalUserController {

    private CreateExternalUser view;
    private final FakeRestFactory restFactory = new FakeRestFactory();
    private CreateExternalUserController controller;

    @BeforeEach
    void setUp() {
        // The REST resource is made with GWT.create, and the form is a widget, which only work in a
        // browser
        GWTMockUtilities.disarm();
        view = Mockito.mock(CreateExternalUser.class);
        controller = new CreateExternalUserController(new SimpleEventBus(), restFactory).attach(view);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void create_noSubjectId(final String subjectId) {
        // Regression test: OK with no unique identity asked the server to create a user with none
        Mockito.when(view.getSubjectId()).thenReturn(subjectId);
        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);

        controller.create(user -> {
        }, okPressed);

        Mockito.verify(view).setSubjectIdInvalid("You must provide the user's unique identity");
        Mockito.verify(okPressed).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
        assertThat(restFactory.getRequests())
                .isEmpty();
    }

    @Test
    void create_subjectId() {
        Mockito.when(view.getSubjectId()).thenReturn("  bob ");
        Mockito.when(view.getDisplayName()).thenReturn("Bob");
        Mockito.when(view.getFullName()).thenReturn("Bob Smith");
        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);

        controller.create(user -> {
        }, okPressed);

        // The user is created with the identity trimmed
        final UserResource userResource = Mockito.mock(UserResource.class);
        restFactory.getLastRequest().callOn(userResource);
        Mockito.verify(userResource).createUser(new UserDesc("bob", "Bob", "Bob Smith"));
        Mockito.verify(view, Mockito.never()).setSubjectIdInvalid(Mockito.anyString());
        Mockito.verify(okPressed, Mockito.never()).reset();
    }
}
