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

package stroom.analytics.client.component;

import stroom.editor.client.component.CodeEditor;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// An analytic notification's email settings: who it goes to, and the templates of its subject
/// and body, each of which can be tested, and a button that sends a test email.
public class AnalyticEmailDestination extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox to;
    @UiField
    TextBox cc;
    @UiField
    TextBox bcc;
    @UiField
    CodeEditor subjectTemplate;
    @UiField
    Button testSubjectTemplateBtn;
    @UiField
    CodeEditor bodyTemplate;
    @UiField
    Button testBodyTemplateBtn;
    @UiField
    Button sendTestEmailBtn;

    private Runnable onChange = () -> {
    };

    /// Makes empty settings.
    public AnalyticEmailDestination() {
        initWidget(BINDER.createAndBindUi(this));
        to.addKeyUpHandler(e -> onChange.run());
        cc.addKeyUpHandler(e -> onChange.run());
        bcc.addKeyUpHandler(e -> onChange.run());
    }

    /// @return The subject template's editor, for its controller to attach to.
    public CodeEditor getSubjectTemplateEditor() {
        return subjectTemplate;
    }

    /// @return The body template's editor, for its controller to attach to.
    public CodeEditor getBodyTemplateEditor() {
        return bodyTemplate;
    }

    /// @return The recipients, separated by semicolons.
    public String getTo() {
        return to.getValue();
    }

    /// @param to The recipients, separated by semicolons.
    public void setTo(final String to) {
        this.to.setValue(to);
    }

    /// @return Who is copied in, separated by semicolons.
    public String getCc() {
        return cc.getValue();
    }

    /// @param cc Who is copied in, separated by semicolons.
    public void setCc(final String cc) {
        this.cc.setValue(cc);
    }

    /// @return Who is blind copied in, separated by semicolons.
    public String getBcc() {
        return bcc.getValue();
    }

    /// @param bcc Who is blind copied in, separated by semicolons.
    public void setBcc(final String bcc) {
        this.bcc.setValue(bcc);
    }

    /// @return Send Test Email, whose spinner shows while the email is sent.
    public TaskMonitorFactory getSendTestEmailTaskMonitor() {
        return sendTestEmailBtn;
    }

    /// @param onChange What to run when the user changes a recipient.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param onTestSubjectTemplate What to run when the user presses the subject's Test Template.
    public void setOnTestSubjectTemplate(final Runnable onTestSubjectTemplate) {
        testSubjectTemplateBtn.setOnClick(onTestSubjectTemplate);
    }

    /// @param onTestBodyTemplate What to run when the user presses the body's Test Template.
    public void setOnTestBodyTemplate(final Runnable onTestBodyTemplate) {
        testBodyTemplateBtn.setOnClick(onTestBodyTemplate);
    }

    /// @param onSendTestEmail What to run when the user presses Send Test Email.
    public void setOnSendTestEmail(final Runnable onSendTestEmail) {
        sendTestEmailBtn.setOnClick(onSendTestEmail);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AnalyticEmailDestination> {

    }
}
