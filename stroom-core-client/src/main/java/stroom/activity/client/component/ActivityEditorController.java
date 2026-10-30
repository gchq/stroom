/*
 * Copyright 2018 Crown Copyright
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

package stroom.activity.client.component;

import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.ActivityDetails;
import stroom.activity.shared.ActivityResource;
import stroom.activity.shared.ActivityValidationResult;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.ui.config.shared.ActivityConfig;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Edits an activity ([ActivityEditor]) in a dialog made for one use, validating and saving it on OK.
public class ActivityEditorController extends Controller {

    private static final ActivityResource ACTIVITY_RESOURCE = GWT.create(ActivityResource.class);

    private final ActivityEditor view;
    private final RestFactory restFactory;

    /// Made by GIN, with its form.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The form.
    /// @param restFactory Validates and saves the activity.
    @Inject
    public ActivityEditorController(final EventBus eventBus,
                                    final ActivityEditor view,
                                    final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
    }

    /// Shows an activity in a dialog, owned by `opener` until it is closed, with the form and
    /// caption the activity config gives. OK validates and saves it.
    ///
    /// @param opener   What shows it.
    /// @param activity The activity, new (no id) or saved.
    /// @param config   The activity config, which says how the form looks.
    /// @param onClosed Given the activity saved on OK, or `activity` on Cancel.
    public void edit(final HasControllerNode opener,
                     final Activity activity,
                     final ActivityConfig config,
                     final Consumer<Activity> onClosed) {
        view.setBody(config.getEditorBody());
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(640, 480))
                .caption(config.getEditorTitle())
                .modal(true)
                .onShow(e -> view.setActivity(activity))
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        validate(activity, onClosed, e);
                    } else {
                        onClosed.accept(activity);
                        e.hide();
                    }
                })
                .fire();
    }

    private void validate(final Activity activity,
                          final Consumer<Activity> onSaved,
                          final HidePopupRequestEvent event) {
        final ActivityDetails details = view.getDetails();
        final Activity updated = activity.copy().details(details).build();
        restFactory
                .create(ACTIVITY_RESOURCE)
                .method(res -> res.validate(updated))
                .onSuccess(result -> afterValidation(result, activity, details, onSaved, event))
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .exec();
    }

    private void afterValidation(final ActivityValidationResult validationResult,
                                 final Activity activity,
                                 final ActivityDetails details,
                                 final Consumer<Activity> onSaved,
                                 final HidePopupRequestEvent event) {
        if (!validationResult.isValid()) {
            AlertEvent.fireWarn(this, "Validation Error", validationResult.getMessages(), event::reset);
        } else if (activity.getId() == null) {
            // A new activity is made first, then saved with its details
            restFactory
                    .create(ACTIVITY_RESOURCE)
                    .method(ActivityResource::create)
                    .onSuccess(result -> update(result.copy().details(details).build(), onSaved, event))
                    .onFailure(RestErrorHandler.forPopup(this, event))
                    .owner(this)
                    .exec();
        } else {
            update(activity.copy().details(details).build(), onSaved, event);
        }
    }

    private void update(final Activity activity,
                        final Consumer<Activity> onSaved,
                        final HidePopupRequestEvent event) {
        restFactory
                .create(ACTIVITY_RESOURCE)
                .method(res -> res.update(activity.getId(), activity))
                .onSuccess(result -> {
                    onSaved.accept(result);
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .exec();
    }
}
