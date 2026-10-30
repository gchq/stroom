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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.FakeRestFactory;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestCreateMultipleUsersController {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<AlertEvent> alerts = new ArrayList<>();
    private final List<ConfirmEvent> confirms = new ArrayList<>();
    private final List<User> created = new ArrayList<>();
    private final FakeRestFactory restFactory = new FakeRestFactory();
    private HidePopupRequestEvent okPressed;

    @BeforeEach
    void setUp() {
        // The REST resource is made with GWT.create, and the form is a widget, which only work in a
        // browser
        GWTMockUtilities.disarm();
        eventBus.addHandler(AlertEvent.getType(), alerts::add);
        eventBus.addHandler(ConfirmEvent.getType(), confirms::add);
        okPressed = Mockito.mock(HidePopupRequestEvent.class);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void aFailedRestoreLetsTheDialogBeUsedAgain() {
        // Regression test: restoring a deleted user had no failure handler, so when it failed the
        // dialog was left with OK and Cancel disabled.
        createAndRestoreCarol();

        restFactory.getLastRequest().fail(new RuntimeException("Refused"));

        assertThat(alerts)
                .hasSize(1);
        Mockito.verify(okPressed, Mockito.never()).reset();
        alerts.getFirst().getCallback().onClose();
        Mockito.verify(okPressed).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
    }

    @Test
    void aRestoreClosesTheDialog() {
        createAndRestoreCarol();
        final User carol = User.builder().uuid("u-carol").subjectId("carol").enabled(true).build();

        restFactory.getLastRequest().succeed(carol);

        Mockito.verify(okPressed).hide();
        assertThat(created)
                .hasSize(1);
        assertThat(alerts)
                .isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\n\n"})
    void noUsersSaysSoAndKeepsTheDialogOpen(final String usersCsvData) {
        // Regression test: OK with no users closed the dialog, as Cancel does, saying nothing
        final CreateMultipleUsers view = Mockito.mock(CreateMultipleUsers.class);
        Mockito.when(view.getUsersCsvData()).thenReturn(usersCsvData);
        final CreateMultipleUsersController controller = new CreateMultipleUsersController(eventBus, restFactory)
                .attach(view);

        controller.create(created::add, okPressed);

        Mockito.verify(view).setUsersCsvDataInvalid("You must provide at least one user");
        Mockito.verify(okPressed).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
        assertThat(restFactory.getRequests())
                .isEmpty();
    }

    @Test
    void usersAreCreatedFromTheirCsv() {
        final CreateMultipleUsers view = Mockito.mock(CreateMultipleUsers.class);
        Mockito.when(view.getUsersCsvData()).thenReturn("carol\ndave");
        final CreateMultipleUsersController controller = new CreateMultipleUsersController(eventBus, restFactory)
                .attach(view);

        controller.create(created::add, okPressed);

        final UserResource userResource = Mockito.mock(UserResource.class);
        restFactory.getLastRequest().callOn(userResource);
        Mockito.verify(userResource).createUsersFromCsv("carol\ndave");
        Mockito.verify(view, Mockito.never()).setUsersCsvDataInvalid(Mockito.anyString());
        Mockito.verify(okPressed, Mockito.never()).reset();
    }

    // Creates 'carol', who was deleted, and agrees to restore her.
    private void createAndRestoreCarol() {
        final CreateMultipleUsers view = Mockito.mock(CreateMultipleUsers.class);
        Mockito.when(view.getUsersCsvData()).thenReturn("carol");
        final CreateMultipleUsersController controller = new CreateMultipleUsersController(eventBus, restFactory)
                .attach(view);

        controller.create(created::add, okPressed);
        restFactory.getLastRequest().succeed(new ArrayList<>(List.of(
                User.builder().uuid("u-carol").subjectId("carol").enabled(false).build())));
        assertThat(confirms)
                .hasSize(1);
        confirms.getFirst().getCallback().onResult(true);
        assertThat(restFactory.getRequests())
                .hasSize(2);
    }
}
