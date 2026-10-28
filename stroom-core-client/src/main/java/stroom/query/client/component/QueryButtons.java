/*
 * Copyright 2022 Crown Copyright
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

package stroom.query.client.component;

import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

/// A query's run button: Execute Query (Ctrl+Enter), which becomes Stop Query (Escape) while the
/// query runs. The BEM block `query-buttons` (`query-buttons.css`), its markup `QueryButtons.ui.xml`.
public class QueryButtons extends Composite {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String PLAY = "query-buttons__button--play";
    private static final String STOP = "query-buttons__button--stop";

    @UiField
    ToolbarButton start;

    private Runnable onStart;

    /// Makes the button, to run the query.
    public QueryButtons() {
        initWidget(BINDER.createAndBindUi(this));
        start.setOnClick(this::onStartClick);
    }

    /// @param onStart Run when the button is pressed, to run the query or, while it runs, to stop it,
    ///                or null for nothing.
    public void setOnStart(final Runnable onStart) {
        this.onStart = onStart;
    }

    private void onStartClick() {
        if (onStart != null) {
            onStart.run();
        }
    }

    /// Shows whether the query is running: the button stops it while it does.
    ///
    /// @param searching Whether the query is running.
    public void setSearching(final boolean searching) {
        start.setStyleName(STOP, searching);
        start.setStyleName(PLAY, !searching);
        if (searching) {
            start.setSvg(SvgImage.STOP);
            // Escape stops a dashboard's or a query's search, as Ctrl+Enter starts it
            start.setShortcut(Action.CLOSE);
            start.setTitle("Stop Query");
        } else {
            start.setSvg(SvgImage.PLAY);
            start.setShortcut(Action.OK);
            start.setTitle("Execute Query");
        }
    }

    /// @param enabled Whether the button can be pressed (e.g. not while the query has no data source).
    public void setEnabled(final boolean enabled) {
        start.setEnabled(enabled);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, QueryButtons> {

    }
}
