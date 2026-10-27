/*
 * Copyright 2024 Crown Copyright
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
import stroom.util.shared.UserDesc;
import stroom.widget.component.client.Controller;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// Adds the identity provider user entered in the [CreateExternalUser] it is attached to. A
/// deleted user of the same identity can be restored.
public class CreateExternalUserController extends Controller {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final RestFactory restFactory;
    private CreateExternalUser view;

    /// Made by GIN; [#attach(CreateExternalUser)] gives it its form.
    ///
    /// @param eventBus    The application's event bus.
    /// @param restFactory Creates and restores the user.
    @Inject
    public CreateExternalUserController(final EventBus eventBus, final RestFactory restFactory) {
        super(eventBus);
        this.restFactory = restFactory;
    }

    /// Attaches this to the form whose user it adds.
    ///
    /// @param view The form, e.g. one a template declares.
    /// @return This, to call on.
    public CreateExternalUserController attach(final CreateExternalUser view) {
        this.view = Objects.requireNonNull(view, "view");
        return this;
    }

    /// Adds the user entered, when OK is pressed on the dialog the form is shown in. With no
    /// identity it says so under the field and keeps the dialog open.
    ///
    /// @param consumer Given the user once they are added (or restored).
    /// @param event    The dialog's OK.
    public void create(final Consumer<User> consumer, final HidePopupRequestEvent event) {
        final String subjectId = NullSafe.trim(view.getSubjectId());
        if (subjectId.isEmpty()) {
            // Said under the field, which is focused, so a screen reader reads it
            view.setSubjectIdInvalid("You must provide the user's unique identity");
            event.reset();
            return;
        }
        final UserDesc userDesc = new UserDesc(
                subjectId,
                view.getDisplayName(),
                view.getFullName());
        restFactory
                .create(USER_RESOURCE)
                .method(res -> res.createUser(userDesc))
                .onSuccess(result -> {
                    if (!result.isEnabled()) {
                        enable(result, consumer, event);
                    } else {
                        consumer.accept(result);
                        event.hide();
                    }
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .exec();
    }

    private void enable(final User user,
                        final Consumer<User> consumer,
                        final HidePopupRequestEvent event) {
        ConfirmEvent.fire(this,
                "A deleted user already exists with the same name, " +
                "would you like to restore the existing user?",
                ok -> {
                    if (ok) {
                        update(user.copy().enabled(true).build(), consumer, event);
                    } else {
                        consumer.accept(user);
                        event.hide();
                    }
                });
    }

    private void update(final User user,
                        final Consumer<User> consumer,
                        final HidePopupRequestEvent event) {
        restFactory
                .create(USER_RESOURCE)
                .method(res -> res.update(user))
                .onSuccess(result -> {
                    consumer.accept(result);
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .exec();
    }
}
