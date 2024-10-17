/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.security.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.entity.client.presenter.NameDocumentView;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.view.DialogActionUiHandlers;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class CreateNewGroupPresenter extends MyPresenterWidget<NameDocumentView> {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final RestFactory restFactory;

    @Inject
    public CreateNewGroupPresenter(final EventBus eventBus,
                                   final NameDocumentView view,
                                   final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
    }

    public void setDialogActionUiHandlers(final DialogActionUiHandlers dialogActionUiHandlers) {
        getView().setUiHandlers(dialogActionUiHandlers);
    }

    public void create(final Consumer<User> consumer,
                       final HidePopupRequestEvent event,
                       final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(USER_RESOURCE)
                .method(res -> res.createGroup(getView().getName()))
                .onSuccess(result -> {
                    if (!result.isEnabled()) {
                        enable(result, consumer, event, taskMonitorFactory);
                    } else {
                        consumer.accept(result);
                        event.hide();
                    }
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    private void enable(final User user,
                        final Consumer<User> consumer,
                        final HidePopupRequestEvent event,
                        final TaskMonitorFactory taskMonitorFactory) {
        ConfirmEvent.fire(this,
                "A deleted group already exists with the same name, " +
                        "would you like to restore the existing group?",
                ok -> {
                    if (ok) {
                        user.setEnabled(true);
                        update(user, consumer, event, taskMonitorFactory);
                    } else {
                        consumer.accept(user);
                        event.hide();
                    }
                });
    }

    private void update(final User user,
                        final Consumer<User> consumer,
                        final HidePopupRequestEvent event,
                        final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(USER_RESOURCE)
                .method(res -> res.update(user))
                .onSuccess(result -> {
                    consumer.accept(result);
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
