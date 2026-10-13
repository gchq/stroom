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
import stroom.widget.layout.client.SplitPane;

import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class ElementViewImpl extends ViewImpl implements ElementView {

    private static final String LOG_PANE_SIZE = "15%";

    private Widget widget;
    // The panes stacked one above another: the code (if any), the input beside the output (or the
    // output alone), and the log
    private SplitPane layout;

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

            // The output, beside the input if there is one
            final Widget middle;
            if (input == null) {
                middle = outputWidget;
            } else {
                final Widget inputWidget = input.asWidget();
                inputWidget.addStyleName("dashboard-panel overflow-hidden");
                final SplitPane inputAndOutput = thinSplitPane("50%, *", "Input, Output");
                inputAndOutput.add(inputWidget);
                inputAndOutput.add(outputWidget);
                middle = inputAndOutput;
            }

            if (code == null) {
                layout = thinSplitPane("*, " + LOG_PANE_SIZE, "Output, Log");
            } else {
                final Widget codeWidget = code.asWidget();
                codeWidget.addStyleName("dashboard-panel overflow-hidden");
                layout = thinSplitPane("50%, *, " + LOG_PANE_SIZE, "Code, Output, Log");
                layout.add(codeWidget);
            }
            layout.setVertical(true);
            layout.add(middle);
            layout.add(logWidget);
            layout.setPaneHidden(logWidget, true);

            widget = layout;
        }

        widget.setSize("100%", "100%");
        return widget;
    }

    private static SplitPane thinSplitPane(final String sizes, final String names) {
        final SplitPane splitPane = new SplitPane();
        splitPane.setThin(true);
        splitPane.setSizes(sizes);
        splitPane.setNames(names);
        return splitPane;
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
            layout.setPaneHidden(log.asWidget(), !isVisible);
        }
    }
}
