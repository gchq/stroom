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

package stroom.main.client.presenter;

import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.dom.client.HasDoubleClickHandlers;
import com.google.gwt.event.shared.GwtEvent.Type;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ContentSlot;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.Proxy;
import com.gwtplatform.mvp.client.proxy.RevealContentHandler;
import com.gwtplatform.mvp.client.proxy.RevealRootContentEvent;
import stroom.content.client.event.RefreshCurrentContentTabEvent;
import stroom.core.client.KeyboardInterceptor;
import stroom.properties.global.client.ClientPropertyCache;
import stroom.properties.shared.ClientProperties;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.task.client.event.OpenTaskManagerEvent;
import stroom.widget.tab.client.event.MaximiseEvent;
import stroom.widget.util.client.DoubleSelectTest;

public class MainPresenter extends MyPresenter<MainPresenter.MainView, MainPresenter.MainProxy> {

    @ContentSlot
    public static final Type<RevealContentHandler<?>> MENUBAR = new Type<>();
    @ContentSlot
    public static final Type<RevealContentHandler<?>> EXPLORER = new Type<>();
    @ContentSlot
    public static final Type<RevealContentHandler<?>> CONTENT = new Type<>();
    private final Timer refreshTimer;
    private boolean click;

    @Inject
    public MainPresenter(final EventBus eventBus,
                         final MainView view,
                         final MainProxy proxy,
                         final KeyboardInterceptor keyboardInterceptor,
                         final ClientPropertyCache clientPropertyCache) {
        super(eventBus, view, proxy);

        // Handle key presses.
        keyboardInterceptor.register(view.asWidget());

        addRegisteredHandler(TaskStartEvent.getType(), event -> {
            // DebugPane.debug("taskStart:" + event.getTaskCount());

            // Always try and start spinner even if it might be spinning
            // already.
            if (view.getSpinner() != null) {
                view.getSpinner().start();
            }
        });
        addRegisteredHandler(TaskEndEvent.getType(), event -> {
            // DebugPane.debug("taskEnd:" + event.getTaskCount());

            // Only stop spinner if we are no longer executing any tasks.
            if (event.getTaskCount() == 0) {
                if (view.getSpinner() != null) {
                    view.getSpinner().stop();
                }
            }
        });
        registerHandler(clientPropertyCache.addPropertyChangeHandler(
                event -> getView().setBanner(event.getProperties().get(ClientProperties.MAINTENANCE_MESSAGE))
                ));

        registerHandler(view.getSpinner().addClickHandler(event -> {
            if (click) {
                click = false;
                OpenTaskManagerEvent.fire(MainPresenter.this);

            } else {
                final Timer clickTimer = new Timer() {
                    @Override
                    public void run() {
                        if (click) {
                            stopAutoRefresh();
                            // refresh();
                            startAutoRefresh();
                        }

                        click = false;
                    }
                };
                click = true;
                clickTimer.schedule(DoubleSelectTest.DOUBLE_SELECT_DELAY);
            }
        }));
        addRegisteredHandler(MaximiseEvent.getType(), event -> view.maximise(event.getView()));

        refreshTimer = new Timer() {
            @Override
            public void run() {
                RefreshCurrentContentTabEvent.fire(MainPresenter.this);
            }
        };

        // Start the auto refresh timer.
        startAutoRefresh();
    }

    private void startAutoRefresh() {
        refreshTimer.scheduleRepeating(30000); // 30 second default.
    }

    private void stopAutoRefresh() {
        refreshTimer.cancel();
    }

    @Override
    protected void revealInParent() {
        RevealRootContentEvent.fire(this, this);
        RootPanel.get("logo").setVisible(false);
    }

    @ProxyCodeSplit
    public interface MainProxy extends Proxy<MainPresenter> {
    }

    public interface MainView extends View {
        SpinnerDisplay getSpinner();

        void maximise(View view);

        void setBanner(String text);
    }

    public interface SpinnerDisplay extends HasClickHandlers, HasDoubleClickHandlers {
        void start();

        void stop();
    }
}
