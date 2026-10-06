/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.widgets;

import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

/// A presenter whose view is a given widget, for showing story content in Stroom's popups and
/// dialogs with `ShowPopupEvent`, which only shows presenters. It stands in for the presenter of
/// whatever screen a Stroom dialog would hold.
public final class ContentPresenter extends MyPresenterWidget<View> {

    /// Creates a presenter showing a widget.
    ///
    /// @param eventBus The event bus that the popup will be shown with.
    /// @param content  The widget to show.
    public ContentPresenter(final EventBus eventBus, final Widget content) {
        super(eventBus, new ContentView(content));
    }

    /// Creates a presenter showing HTML, e.g. some paragraphs.
    ///
    /// @param eventBus The event bus that the popup will be shown with.
    /// @param html     The (trusted, constant) HTML to show.
    /// @return The presenter.
    public static ContentPresenter html(final EventBus eventBus, final String html) {
        return new ContentPresenter(eventBus, new HTML(html));
    }


    // --------------------------------------------------------------------------------


    private static final class ContentView extends ViewImpl {

        private final Widget content;

        private ContentView(final Widget content) {
            this.content = content;
        }

        @Override
        public Widget asWidget() {
            return content;
        }
    }
}
