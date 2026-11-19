/*
 * Copyright 2016 Crown Copyright
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

import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// Adds the identity provider users entered in the [CreateMultipleUsers] it is attached to.
/// Deleted users of the same identities can be restored.
public class CreateMultipleUsersController extends Controller {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final RestFactory restFactory;
    private CreateMultipleUsers view;

    /// Made by GIN; [#attach(CreateMultipleUsers)] gives it its form.
    ///
    /// @param eventBus    The application's event bus.
    /// @param restFactory Creates and restores the users.
    @Inject
    public CreateMultipleUsersController(final EventBus eventBus, final RestFactory restFactory) {
        super(eventBus);
        this.restFactory = restFactory;
    }

    /// Attaches this to the form whose users it adds.
    ///
    /// @param view The form, e.g. one a template declares.
    /// @return This, to call on.
    public CreateMultipleUsersController attach(final CreateMultipleUsers view) {
        this.view = Objects.requireNonNull(view, "view");
        return this;
    }

    /// Adds the users entered, when OK is pressed on the dialog the form is shown in. With no
    /// users it says so under the field and keeps the dialog open.
    ///
    /// @param consumer Given the first user once they are added (or restored).
    /// @param event    The dialog's OK.
    public void create(final Consumer<User> consumer, final HidePopupRequestEvent event) {
        final String usersCsvData = view.getUsersCsvData();
        if (NullSafe.isBlankString(usersCsvData)) {
            // Said under the field, which is focused, so a screen reader reads it
            view.setUsersCsvDataInvalid("You must provide at least one user");
            event.reset();
        } else {
            restFactory
                    .create(USER_RESOURCE)
                    .method(res -> res.createUsersFromCsv(usersCsvData))
                    .onSuccess(result -> {
                        if (result != null) {
                            final List<User> disabled = result
                                    .stream()
                                    .filter(user -> !user.isEnabled())
                                    .collect(Collectors.toList());
                            final Optional<User> nextSelection = result.stream().findFirst();
                            if (!disabled.isEmpty()) {
                                ConfirmEvent.fire(this,
                                        "Some deleted users already exist with the same names, " +
                                        "would you like to restore them?",
                                        ok -> {
                                            if (ok) {
                                                enable(disabled, consumer, event, nextSelection);
                                            } else {
                                                nextSelection.ifPresent(consumer);
                                                event.hide();
                                            }
                                        });
                            } else {
                                nextSelection.ifPresent(consumer);
                                event.hide();
                            }
                        } else {
                            event.hide();
                        }
                    })
                    .onFailure(RestErrorHandler.forPopup(this, event))
                    .owner(this)
                    .exec();
        }
    }

    private void enable(final List<User> disabled,
                        final Consumer<User> consumer,
                        final HidePopupRequestEvent event,
                        final Optional<User> nextSelection) {
        if (disabled.isEmpty()) {
            nextSelection.ifPresent(consumer);
            event.hide();
        } else {
            final User user = disabled.remove(0).copy().enabled(true).build();
            restFactory
                    .create(USER_RESOURCE)
                    .method(res -> res.update(user))
                    .onSuccess(result -> enable(disabled, consumer, event, nextSelection))
                    // If a user can't be restored, let the user try again or cancel
                    .onFailure(RestErrorHandler.forPopup(this, event))
                    .owner(this)
                    .exec();
        }
    }
}
