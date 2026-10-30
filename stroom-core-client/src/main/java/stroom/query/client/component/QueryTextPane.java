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

package stroom.query.client.component;

import stroom.data.client.component.ClassificationLabel;
import stroom.editor.client.component.CodeEditor;
import stroom.editor.client.component.HtmlPane;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.spinner.client.LargeBusySpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The source of a query result's row, beside the results: the stream's text in a code editor,
/// or its HTML, with its classification, and a button that closes the pane. It shows a spinner
/// while the source loads.
public class QueryTextPane extends Component implements TaskMonitorFactory {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ToolbarButton close;
    @UiField
    CodeEditor editor;
    @UiField
    HtmlPane html;
    @UiField
    ClassificationLabel classification;
    @UiField
    LargeBusySpinner spinner;

    private Consumer<String> onLink;

    /// Makes an empty pane.
    public QueryTextPane() {
        initWidget(BINDER.createAndBindUi(this));
        html.addDomHandler(event -> {
            final Element target = event.getNativeEvent().getEventTarget().cast();
            final String link = target.getAttribute("link");
            if (onLink != null && link != null && !link.isEmpty()) {
                onLink.accept(link);
            }
        }, ClickEvent.getType());
    }

    /// @return The code editor, for its controller to attach to.
    public CodeEditor getEditor() {
        return editor;
    }

    /// @return The classification label, for its controller to attach to.
    public ClassificationLabel getClassificationLabel() {
        return classification;
    }

    /// Shows the code editor, hiding the HTML.
    public void showEditor() {
        html.setVisible(false);
        editor.setVisible(true);
    }

    /// Shows HTML, hiding the code editor.
    ///
    /// @param html The HTML.
    public void showHtml(final String html) {
        editor.setVisible(false);
        this.html.setVisible(true);
        this.html.setHtml(html);
    }

    /// @param classification The classification of what is shown.
    public void setClassification(final String classification) {
        this.classification.setClassification(classification);
    }

    /// @param onLink Told the `link` attribute of what the user clicks in the HTML, e.g. a
    ///               hyperlink to open.
    public void setOnLink(final Consumer<String> onLink) {
        this.onLink = onLink;
    }

    /// @param onClose What to run when the user presses Close.
    public void setOnClose(final Runnable onClose) {
        close.setOnClick(onClose);
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return spinner.createTaskMonitor();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, QueryTextPane> {

    }
}
