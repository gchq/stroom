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

package stroom.dashboard.client.query;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.ProvidesResize;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.dashboard.client.main.SearchModel.Mode;
import stroom.dashboard.client.query.QueryPresenter.QueryView;
import stroom.svg.client.SvgPreset;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.FabButton;
import stroom.widget.layout.client.view.ResizeSimplePanel;

public class QueryViewImpl extends ViewWithUiHandlers<QueryUiHandlers>
        implements QueryView, RequiresResize, ProvidesResize {
    private final Widget widget;

    @UiField
    ResizeSimplePanel expressionTree;
    @UiField
    ButtonPanel buttonPanel;
    @UiField
    StartButton start;
    @UiField
    FabButton stop;

    @Inject
    public QueryViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void onResize() {
        ((RequiresResize) widget).onResize();
    }

    @Override
    public ButtonView addButton(final SvgPreset preset) {
        return buttonPanel.add(preset);
    }

    @Override
    public void setExpressionView(final View view) {
        expressionTree.setWidget(view.asWidget());
    }

    @UiHandler("start")
    public void onStartClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().start();
        }
    }

    @UiHandler("stop")
    public void onStopClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().stop();
        }
    }

    @Override
    public void setMode(final Mode mode) {
        switch (mode) {
            case ACTIVE:
                start.pauseMode();
                stop.setEnabled(true);
                break;
            case INACTIVE:
                start.searchMode();
                stop.setEnabled(false);
                break;
            case PAUSED:
                start.resumeMode();
                stop.setEnabled(true);
                break;
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        start.setEnabled(enabled);
    }

    public interface Binder extends UiBinder<Widget, QueryViewImpl> {
    }
}
