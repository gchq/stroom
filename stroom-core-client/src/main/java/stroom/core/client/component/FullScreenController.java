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

package stroom.core.client.component;

import stroom.core.client.UrlParameters;
import stroom.core.client.event.ShowFullScreenEvent;
import stroom.entity.client.component.DocumentEditorHost;
import stroom.widget.component.client.Controller;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;


/// Shows a document as the whole page, named for it in the browser's title.
public class FullScreenController extends Controller
        implements ShowFullScreenEvent.Handler {

    private final FullScreen view;

    private final UrlParameters urlParameters;

    /// Made by GIN, with its screen and its parts.
    @Inject
    public FullScreenController(final EventBus eventBus,
                               final FullScreen view,
                               final UrlParameters urlParameters) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.urlParameters = urlParameters;
    }

    @Override
    public void onShowFullScreen(final ShowFullScreenEvent e) {
        final MyPresenterWidget<?> presenterWidget = e.getPresenterWidget();
        view.setContentView(presenterWidget);

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
}
