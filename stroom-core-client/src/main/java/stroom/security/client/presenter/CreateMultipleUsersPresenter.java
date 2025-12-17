/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.presenter.CreateMultipleUsersPresenter.CreateMultipleUsersView;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CreateMultipleUsersPresenter extends MyPresenterWidget<CreateMultipleUsersView> {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final RestFactory restFactory;

    @Inject
    public CreateMultipleUsersPresenter(final EventBus eventBus,
                                        final CreateMultipleUsersView view,
                                        final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
    }

    public void create(final Consumer<User> consumer,
                       final HidePopupRequestEvent event,
                       final TaskMonitorFactory taskMonitorFactory) {
        final String usersCsvData = getView().getUsersCsvData();
        if (usersCsvData != null && !usersCsvData.isEmpty()) {
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
                                                enable(disabled, consumer, event, nextSelection, taskMonitorFactory);
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
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        } else {
            event.hide();
        }
    }

    private void enable(final List<User> disabled,
                        final Consumer<User> consumer,
                        final HidePopupRequestEvent event,
                        final Optional<User> nextSelection,
                        final TaskMonitorFactory taskMonitorFactory) {
        if (disabled.size() == 0) {
            nextSelection.ifPresent(consumer);
            event.hide();
        } else {
            final User user = disabled.remove(0);
            user.setEnabled(true);
            restFactory
                    .create(USER_RESOURCE)
                    .method(res -> res.update(user))
                    .onSuccess(result -> {
                        enable(disabled, consumer, event, nextSelection, taskMonitorFactory);
                    })
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        }
    }

    public interface CreateMultipleUsersView extends View {

        String getUsersCsvData();

        void focus();

        void clear();
    }
}
