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

package stroom.app.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.annotations.ProxyStandard;
import com.gwtplatform.mvp.client.proxy.Proxy;
import stroom.security.client.ClientSecurityContext;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskEndEvent.TaskEndHandler;
import stroom.task.client.TaskStartEvent;
import stroom.task.client.TaskStartEvent.TaskStartHandler;

public class AppPresenter extends MyPresenter<AppPresenter.AppView, AppPresenter.AppProxy>
        implements TaskStartHandler, TaskEndHandler {
    @ProxyStandard
    public interface AppProxy extends Proxy<AppPresenter> {
    }

    public interface AppView extends View {
        void showWorking(final String message);

        void hideWorking();
    }

    private final ClientSecurityContext securityContext;

    @Inject
    public AppPresenter(final EventBus eventBus, final AppView view, final AppProxy proxy,
            final ClientSecurityContext securityContext) {
        super(eventBus, view, proxy);
        this.securityContext = securityContext;
    }

    @ProxyEvent
    @Override
    public void onTaskStart(final TaskStartEvent event) {
        if (!securityContext.isLoggedIn()) {
            getView().showWorking(event.getMessage());
        }
    }

    @ProxyEvent
    @Override
    public void onTaskEnd(final TaskEndEvent event) {
        if (event.getTaskCount() == 0) {
            getView().hideWorking();
        }
    }

    @Override
    protected void revealInParent() {
        // Do nothing as this presenter merely deals with the loading spinner.
    }
}
