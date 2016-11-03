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

import com.google.gwt.user.client.ui.MySplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

import stroom.pipeline.stepping.client.presenter.EditorPresenter.EditorView;

public class EditorViewImpl extends ViewImpl implements EditorView {
    private Widget widget;

    private View code;
    private View input;
    private View output;

    @Override
    public Widget asWidget() {
        if (widget == null) {
            if (input == null) {
                final Widget outputWidget = output.asWidget();
                widget = outputWidget;

            } else if (code == null) {
                // Create layout.
                final Widget inputWidget = input.asWidget();
                final Widget outputWidget = output.asWidget();

                inputWidget.getElement().getStyle().setProperty("borderRight", "1px solid #c5cde2");
                outputWidget.getElement().getStyle().setProperty("borderLeft", "1px solid #c5cde2");

                final MySplitLayoutPanel layout = new MySplitLayoutPanel();
                layout.setHSplits("0.5");
                layout.setVSplits("0.66");
                layout.addWest(inputWidget, 200);
                layout.add(outputWidget);
                widget = layout;

            } else {
                // Create layout.
                final Widget codeWidget = code.asWidget();
                final Widget inputWidget = input.asWidget();
                final Widget outputWidget = output.asWidget();

                codeWidget.getElement().getStyle().setProperty("borderBottom", "1px solid #c5cde2");
                inputWidget.getElement().getStyle().setProperty("borderTop", "1px solid #c5cde2");
                inputWidget.getElement().getStyle().setProperty("borderRight", "1px solid #c5cde2");
                outputWidget.getElement().getStyle().setProperty("borderTop", "1px solid #c5cde2");
                outputWidget.getElement().getStyle().setProperty("borderLeft", "1px solid #c5cde2");

                final MySplitLayoutPanel layout = new MySplitLayoutPanel();
                layout.setHSplits("0.5");
                layout.setVSplits("0.66");
                layout.addNorth(codeWidget, 200);
                layout.addWest(inputWidget, 200);
                layout.add(outputWidget);
                widget = layout;
            }
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
}
