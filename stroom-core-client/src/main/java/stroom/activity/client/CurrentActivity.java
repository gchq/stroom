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

package stroom.activity.client;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.activity.shared.Activity;
import stroom.activity.shared.GetCurrentActivityAction;
import stroom.activity.shared.SetCurrentActivityAction;
import stroom.dispatch.client.ClientDispatchAsync;

import javax.inject.Singleton;
import java.util.function.Consumer;

@Singleton
public class CurrentActivity implements HasHandlers {
    private final EventBus eventBus;
    private final Provider<ManageActivityPresenter> manageActivityPresenterProvider;
    private final ClientDispatchAsync dispatcher;

    private Activity currentActivity;
    private boolean fetched;

    @Inject
    public CurrentActivity(final EventBus eventBus,
                           final Provider<ManageActivityPresenter> manageActivityPresenterProvider,
                           final ClientDispatchAsync dispatcher) {
        this.eventBus = eventBus;
        this.manageActivityPresenterProvider = manageActivityPresenterProvider;
        this.dispatcher = dispatcher;
    }

    public void getActivity(final Consumer<Activity> consumer) {
        if (fetched) {
            consumer.accept(currentActivity);
        } else {
            dispatcher.exec(new GetCurrentActivityAction()).onSuccess(a -> {
                currentActivity = a;
                fetched = true;
                consumer.accept(a);
            });
        }
    }

    public void setActivity(final Activity activity) {
        dispatcher.exec(new SetCurrentActivityAction(activity)).onSuccess(a -> {
            currentActivity = a;
            fetched = true;
            ActivityChangedEvent.fire(this, a);
        });
    }

    public void showInitialActivityChooser(final Consumer<Activity> consumer) {
        manageActivityPresenterProvider.get().showInitial(consumer);
    }

    public void showActivityChooser() {
        manageActivityPresenterProvider.get().show(a -> {});
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
