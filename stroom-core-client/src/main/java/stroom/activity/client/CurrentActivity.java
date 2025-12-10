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

package stroom.activity.client;

import stroom.activity.shared.Activity;
import stroom.activity.shared.ActivityResource;
import stroom.dispatch.client.RestFactory;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class CurrentActivity implements HasHandlers {

    private static final ActivityResource ACTIVITY_RESOURCE = GWT.create(ActivityResource.class);

    private final EventBus eventBus;
    private final Provider<ManageActivityPresenter> manageActivityPresenterProvider;
    private final RestFactory restFactory;

    private Activity currentActivity;
    private boolean fetched;

    @Inject
    public CurrentActivity(final EventBus eventBus,
                           final Provider<ManageActivityPresenter> manageActivityPresenterProvider,
                           final RestFactory restFactory) {
        this.eventBus = eventBus;
        this.manageActivityPresenterProvider = manageActivityPresenterProvider;
        this.restFactory = restFactory;
    }

    public void getActivity(final Consumer<Activity> consumer, final TaskMonitorFactory taskMonitorFactory) {
        if (fetched) {
            consumer.accept(currentActivity);
        } else {
            restFactory
                    .create(ACTIVITY_RESOURCE)
                    .method(ActivityResource::getCurrentActivity)
                    .onSuccess(a -> {
                        currentActivity = a;
                        fetched = true;
                        consumer.accept(a);
                    })
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        }
    }

    public void setActivity(final Activity activity, final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ACTIVITY_RESOURCE)
                .method(res -> res.setCurrentActivity(activity))
                .onSuccess(a -> {
                    currentActivity = a;
                    fetched = true;
                    ActivityChangedEvent.fire(this, a);
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void showInitialActivityChooser(final Consumer<Activity> consumer) {
        manageActivityPresenterProvider.get().showInitial(consumer);
    }

    public void showActivityChooser() {
        manageActivityPresenterProvider.get().show(a -> {
        });
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
