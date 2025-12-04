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

package stroom.editor.client.view;

import stroom.editor.client.presenter.SingleLineEditorView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import javax.inject.Inject;

/**
 * This is a widget that can be used to edit text. It provides useful
 * functionality such as formatting, styling, line numbers and warning/error
 * markers.
 */
public class SingleLineEditorViewImpl
        extends AbstractEditorViewImpl
        implements SingleLineEditorView {

    private final Widget widget;

    @UiField(provided = true)
    SimplePanel content;

    @Inject
    public SingleLineEditorViewImpl(final Binder binder) {
        super();
        content = createResizablePanel();
        widget = binder.createAndBindUi(this);
        initOptions();
        content.setWidget(editor.asWidget());

        // Settings for our single line use case
        editor.setMaxLines(1);
        editor.setFirstLineNumber(1);
        lineWrapOption.setOff();
        highlightActiveLineOption.setOff();
        lineNumbersOption.setOff();
        showIndentGuides.setOff();
        showInvisiblesOption.setOff();
        viewAsHexOption.setOff();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    SimplePanel getContentPanel() {
        return content;
    }

    @Override
    public void setText(final String text) {
        content.setWidget(editor.asWidget());
        super.setText(text);
    }

    @Override
    protected String getAdditionalClassNames() {
        return "single-line-editor";
    }

    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<FlowPanel, SingleLineEditorViewImpl> {

    }
}
