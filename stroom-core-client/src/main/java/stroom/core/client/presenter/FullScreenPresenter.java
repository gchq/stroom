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

package stroom.core.client.presenter;

import stroom.core.client.UrlParameters;
import stroom.core.client.event.ShowFullScreenEvent;
import stroom.core.client.presenter.FullScreenPresenter.FullScreenProxy;
import stroom.core.client.presenter.FullScreenPresenter.FullScreenView;
import stroom.entity.client.presenter.DocumentEditTabPresenter;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.PresenterWidget;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ContentSlot;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.annotations.ProxyStandard;
import com.gwtplatform.mvp.client.proxy.Proxy;
import com.gwtplatform.mvp.client.proxy.RevealContentEvent;
import com.gwtplatform.mvp.client.proxy.RevealContentHandler;

import javax.inject.Inject;

public class FullScreenPresenter
        extends MyPresenter<FullScreenView, FullScreenProxy>
        implements ShowFullScreenEvent.Handler {

    @ContentSlot
    public static final GwtEvent.Type<RevealContentHandler<?>> CONTENT = new GwtEvent.Type<>();

    private final UrlParameters urlParameters;

    @Inject
    public FullScreenPresenter(final EventBus eventBus,
                               final FullScreenView view,
                               final FullScreenProxy proxy,
                               final UrlParameters urlParameters) {
        super(eventBus, view, proxy);
        this.urlParameters = urlParameters;
    }

    @ProxyEvent
    @Override
    public void onShowFullScreen(final ShowFullScreenEvent e) {
        final PresenterWidget<?> presenterWidget = e.getPresenterWidget();
        setInSlot(CONTENT, presenterWidget);
        forceReveal();

        final String title = urlParameters.getTitle();
        if (title != null && title.trim().length() > 0) {
            Window.setTitle(title.trim());
        } else {
            if (presenterWidget instanceof DocumentEditTabPresenter<?, ?>) {
                final DocumentEditTabPresenter<?, ?> tabPresenter = (DocumentEditTabPresenter<?, ?>) presenterWidget;
                tabPresenter.addDirtyHandler(event -> Window.setTitle(tabPresenter.getLabel()));
                Window.addWindowClosingHandler(event -> {
                    if (tabPresenter.isDirty()) {
                        event.setMessage(tabPresenter.getType() + " '" + tabPresenter.getLabel() + "' " +
                                "has unsaved changes. Are you sure you want to close it?");
                    }
                });
            }
        }
    }

    @Override
    protected void revealInParent() {
        RevealContentEvent.fire(this, CorePresenter.CORE, this);
        RootPanel.get("logo").setVisible(false);
    }

    @ProxyStandard
    public interface FullScreenProxy extends Proxy<FullScreenPresenter> {

    }

    public interface FullScreenView extends View {

    }
}
