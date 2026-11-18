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

package stroom.entity.client.event;

import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.gwtplatform.mvp.client.PresenterWidget;

/// Fired when a tab within a document's tab (e.g. a Pipeline's Structure or Stepping view) has been selected and
/// its content shown, so that something following the user's active tab (e.g. the AI chat) can notice.
public class DocTabSelectedEvent extends GwtEvent<DocTabSelectedEvent.Handler> {

    private static Type<Handler> TYPE;

    private final TabData docTab;
    private final TabData tab;
    private final PresenterWidget<?> content;

    private DocTabSelectedEvent(final TabData docTab, final TabData tab, final PresenterWidget<?> content) {
        this.docTab = docTab;
        this.tab = tab;
        this.content = content;
    }

    /// @param handlers Where to fire it.
    /// @param docTab   The document's tab, e.g. the Pipeline's presenter.
    /// @param tab      The tab selected within it.
    /// @param content  What the selected tab shows.
    public static void fire(final HasHandlers handlers,
                            final TabData docTab,
                            final TabData tab,
                            final PresenterWidget<?> content) {
        handlers.fireEvent(new DocTabSelectedEvent(docTab, tab, content));
    }

    /// @return The event's type.
    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onDocTabSelected(this);
    }

    /// @return The document's tab, e.g. the Pipeline's presenter.
    public TabData getDocTab() {
        return docTab;
    }

    /// @return The tab selected within it.
    public TabData getTab() {
        return tab;
    }

    /// @return What the selected tab shows.
    public PresenterWidget<?> getContent() {
        return content;
    }


    // --------------------------------------------------------------------------------


    /// Handles [DocTabSelectedEvent].
    public interface Handler extends EventHandler {

        /// @param event The event.
        void onDocTabSelected(DocTabSelectedEvent event);
    }
}
