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

package stroom.pipeline.stepping.client.view;

import stroom.pipeline.stepping.client.presenter.ElementPresenter.ElementView;

import com.google.gwt.user.client.ui.MySplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class ElementViewImpl extends ViewImpl implements ElementView {

    protected static final int CONSOLE_SIZE = 30;
    private Widget widget;
    private MySplitLayoutPanel layout;

    private View code;
    private View input;
    private View output;
    private View console;
    private boolean isConsoleVisible = false;

    @Override
    public Widget asWidget() {
        if (widget == null) {
            if (input == null) {
                final Widget outputWidget = output.asWidget();
                outputWidget.addStyleName("dashboard-panel overflow-hidden");
                layout = new MySplitLayoutPanel();
                final Widget consoleWidget = console.asWidget();
                consoleWidget.addStyleName("dashboard-panel overflow-hidden");
                layout.setVSplits("0.80");
                layout.addSouth(consoleWidget, CONSOLE_SIZE);
                layout.add(outputWidget);
            } else if (code == null) {
                // Create layout.
                final Widget inputWidget = input.asWidget();
                final Widget outputWidget = output.asWidget();
                inputWidget.addStyleName("dashboard-panel overflow-hidden");
                outputWidget.addStyleName("dashboard-panel overflow-hidden");
                layout = new MySplitLayoutPanel();
                layout.setHSplits("0.5");
                layout.addWest(inputWidget, 200);
                final Widget consoleWidget = console.asWidget();
                consoleWidget.addStyleName("dashboard-panel overflow-hidden");
                layout.setVSplits("0.50,0.15");
                layout.addSouth(consoleWidget, CONSOLE_SIZE);
                layout.add(outputWidget);
            } else {
                // Create layout.
                final Widget codeWidget = code.asWidget();
                final Widget inputWidget = input.asWidget();
                final Widget outputWidget = output.asWidget();
                codeWidget.addStyleName("dashboard-panel overflow-hidden");
                inputWidget.addStyleName("dashboard-panel overflow-hidden");
                outputWidget.addStyleName("dashboard-panel overflow-hidden");
                layout = new MySplitLayoutPanel();
                layout.setHSplits("0.5");
                layout.addNorth(codeWidget, 200);
                final Widget consoleWidget = console.asWidget();
                consoleWidget.addStyleName("dashboard-panel overflow-hidden");
                layout.setVSplits("0.50,0.15");
                layout.addSouth(consoleWidget, CONSOLE_SIZE);
                layout.addWest(inputWidget, 200);
                layout.add(outputWidget);
            }
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
    public void setConsoleView(final View view) {
        console = view;
    }

    @Override
    public void setConsoleVisible(final boolean isVisible) {
        isConsoleVisible = isVisible;
        if (layout != null && console != null) {
            layout.setWidgetHidden(console.asWidget(), !isVisible);
        }
    }

    @Override
    public void toggleConsoleVisible() {
        setConsoleVisible(!isConsoleVisible);
    }
}
