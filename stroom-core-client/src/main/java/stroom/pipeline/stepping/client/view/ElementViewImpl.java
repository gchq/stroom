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

package stroom.pipeline.stepping.client.view;

import stroom.pipeline.stepping.client.presenter.ElementPresenter.ElementView;

import com.google.gwt.user.client.ui.ThinSplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class ElementViewImpl extends ViewImpl implements ElementView {

    private static final int LOG_PANE_SIZE = 30;
    private static final double LOG_PANE_SPLIT = 0.15;

    private Widget widget;
    private ThinSplitLayoutPanel layout;

    private View code;
    private View input;
    private View output;
    private View log;
    private boolean isLogVisible = false;

    @Override
    public Widget asWidget() {
        if (widget == null) {
            final Widget outputWidget = output.asWidget();
            final Widget logWidget = log.asWidget();
            outputWidget.addStyleName("dashboard-panel overflow-hidden");
            logWidget.addStyleName("dashboard-panel overflow-hidden");
            layout = new ThinSplitLayoutPanel();

            if (input == null) {
                layout.setVSplits(LOG_PANE_SPLIT);
                layout.addSouth(logWidget, LOG_PANE_SIZE);
            } else {
                final Widget inputWidget = input.asWidget();
                inputWidget.addStyleName("dashboard-panel overflow-hidden");
                layout.setHSplits(0.5);
                if (code == null) {
                    layout.setVSplits(LOG_PANE_SPLIT);
                    layout.addSouth(logWidget, LOG_PANE_SIZE);
                    layout.addWest(inputWidget, 200);
                } else {
                    final Widget codeWidget = code.asWidget();
                    codeWidget.addStyleName("dashboard-panel overflow-hidden");
                    layout.addNorth(codeWidget, 200);
                    layout.setVSplits(0.5, LOG_PANE_SPLIT);
                    layout.addSouth(logWidget, LOG_PANE_SIZE);
                    layout.addWest(inputWidget, 200);
                }
            }
            // Center widget so must be added last
            layout.add(outputWidget);

            layout.setWidgetHidden(logWidget, true);

            widget = layout;
        }

        widget.setSize("100%", "100%");
        return widget;
    }

    @Override
    public void setCodeView(final View view) {
        code = view;
    }

    @Override
    public void setInputView(final View view) {
        input = view;
    }

    @Override
    public void setOutputView(final View view) {
        output = view;
    }

    @Override
    public void setLogView(final View view) {
        log = view;
    }

    @Override
    public void setLogVisible(final boolean isVisible) {
        isLogVisible = isVisible;
        if (layout != null && log != null) {
            layout.setWidgetHidden(log.asWidget(), !isVisible);
            layout.setVSplits();
        }
    }
}
