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

import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.presenter.CreateMultipleUsersPresenter.CreateMultipleUsersView;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.task.client.TaskListener;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

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
                       final TaskListener taskListener) {
        final String usersCsvData = getView().getUsersCsvData();
        if (usersCsvData != null && !usersCsvData.isEmpty()) {
            restFactory
                    .create(USER_RESOURCE)
                    .method(res -> res.createUsersFromCsv(usersCsvData))
                    .onSuccess(result -> {
                        if (result != null && result.size() > 0) {
                            consumer.accept(result.get(0));
                        }
                        event.hide();
                    })
                    .onFailure(RestErrorHandler.forPopup(this, event))
                    .taskListener(taskListener)
                    .exec();
        } else {
            event.hide();
        }
    }

    public interface CreateMultipleUsersView extends View {

        String getUsersCsvData();

        void focus();

        void clear();
    }
}
