/*
 * Copyright 2023 Crown Copyright
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
import stroom.core.client.presenter.FullScreenPresenter.FullScreenView;
import stroom.entity.client.component.DocumentEditorHost;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.user.client.Window;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Inject;

public class FullScreenPresenter
        extends MyPresenterWidget<FullScreenView>
        implements ShowFullScreenEvent.Handler {

    private final UrlParameters urlParameters;

    @Inject
    public FullScreenPresenter(final EventBus eventBus,
                               final FullScreenView view,
                               final UrlParameters urlParameters) {
        super(eventBus, view);
        this.urlParameters = urlParameters;
    }

    @Override
    public void onShowFullScreen(final ShowFullScreenEvent e) {
        final MyPresenterWidget<?> presenterWidget = e.getPresenterWidget();
        getView().setContentView(presenterWidget.getView());

        final String title = urlParameters.getTitle();
        if (title != null && title.trim().length() > 0) {
            Window.setTitle(title.trim());
        } else {
            if (presenterWidget instanceof final DocumentEditorHost<?> tabPresenter) {
                tabPresenter.addDirtyListener(dirty -> Window.setTitle(tabPresenter.getLabel()));
                Window.addWindowClosingHandler(event -> {
                    if (tabPresenter.isDirty()) {
                        event.setMessage(tabPresenter.getType() + " '" + tabPresenter.getLabel() + "' " +
                                         "has unsaved changes. Are you sure you want to close it?");
                    }
                });
            }
        }
    }


    public interface FullScreenView extends View {

        /// Shows the document.
        ///
        /// @param view Its view.
        void setContentView(View view);
    }
}
