/*
 * Copyright 2023 Crown Copyright
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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.analytics.shared.AnalyticRuleResource;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.component.CodeEditorController;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.StringWrapper;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/// An analytic notification's email settings ([AnalyticEmailDestination]): checks them, tests
/// their templates on the server and sends a test email.
public class AnalyticEmailDestinationController extends Controller {

    // TODO Make a jinja mode
    private static final AceEditorMode ACE_EDITOR_MODE = AceEditorMode.TEXT;
    private static final AnalyticRuleResource ANALYTIC_RULE_RESOURCE = GWT.create(AnalyticRuleResource.class);

    private final AnalyticEmailDestination view;
    private final CodeEditorController subjectEditor;
    private final CodeEditorController bodyEditor;
    private final RestFactory restFactory;

    private Runnable onChange = () -> {
    };

    /// Made by GIN, with its settings.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param view                         The settings.
    /// @param codeEditorControllerProvider Makes the templates' editors' controllers.
    /// @param restFactory                  Tests the templates and sends the test email.
    @Inject
    public AnalyticEmailDestinationController(final EventBus eventBus,
                                              final AnalyticEmailDestination view,
                                              final Provider<CodeEditorController> codeEditorControllerProvider,
                                              final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        subjectEditor = make(codeEditorControllerProvider).attach(view.getSubjectTemplateEditor());
        bodyEditor = make(codeEditorControllerProvider).attach(view.getBodyTemplateEditor());
        initEditor(subjectEditor);
        initEditor(bodyEditor);

        view.setOnChange(() -> onChange.run());
        view.setOnSendTestEmail(this::onSendTestEmail);
        view.setOnTestSubjectTemplate(() ->
                testTemplate(NotificationEmailDestination::getSubjectTemplate, ValidationMode.SUBJECT));
        view.setOnTestBodyTemplate(() ->
                testTemplate(NotificationEmailDestination::getBodyTemplate, ValidationMode.BODY));
    }

    /// @return The settings shown, as they are now.
    public NotificationEmailDestination getCurrentEmailDestination() {
        return new NotificationEmailDestination(
                view.getTo(),
                view.getCc(),
                view.getBcc(),
                subjectEditor.getText(),
                bodyEditor.getText());
    }

    /// Shows email settings.
    ///
    /// @param destination The settings, or null to leave what is shown.
    public void read(final NotificationEmailDestination destination) {
        if (destination != null) {
            view.setTo(destination.getTo());
            view.setCc(destination.getCc());
            view.setBcc(destination.getBcc());
            subjectEditor.setText(destination.getSubjectTemplate());
            bodyEditor.setText(destination.getBodyTemplate());
        }
    }

    /// @return The settings shown.
    public NotificationEmailDestination write() {
        return NotificationEmailDestination
                .builder()
                .to(view.getTo())
                .cc(view.getCc())
                .bcc(view.getBcc())
                .subjectTemplate(subjectEditor.getText())
                .bodyTemplate(bodyEditor.getText())
                .build();
    }

    /// @param onChange What to run when the user changes the email settings.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    private void initEditor(final CodeEditorController editor) {
        editor.setMode(ACE_EDITOR_MODE);
        editor.getOptions().getIndicatorsOption().setOff();
        editor.getOptions().getIndicatorsOption().setUnavailable();
        editor.getOptions().getLineWrapOption().setOn();
        editor.getOptions().getLineNumbersOption().setOff();
        editor.getOptions().getViewAsHexOption().setOff();
        editor.getOptions().getViewAsHexOption().setUnavailable();
        editor.getFormatAction().setUnavailable();

        own(editor.addValueChangeHandler(event -> onChange.run()));
    }

    private void testTemplate(final Function<NotificationEmailDestination, String> templateGetter,
                              final ValidationMode validationMode) {
        final NotificationEmailDestination emailDestination = getCurrentEmailDestination();
        final String template = templateGetter.apply(emailDestination);
        if (validate(emailDestination, validationMode)) {
            restFactory
                    .create(ANALYTIC_RULE_RESOURCE)
                    .method(res -> res.testTemplate(StringWrapper.wrap(template)))
                    .onSuccess(output -> {
                        final String msg = "Example template output:";
                        AlertEvent.fireInfo(this, msg, output.getString(), null);
                    })
                    .onFailure(throwable ->
                            AlertEvent.fireError(this, throwable.getMessage(), null))
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

    private void onSendTestEmail() {
        final NotificationEmailDestination emailDestination = getCurrentEmailDestination();

        if (validate(emailDestination)) {
            final String msg = "This will send a single test email using the configured templates and " +
                               "an example detection event to the following recipients:\n"
                               + "To: " + emailDestination.getTo() + "\n"
                               + "Cc: " + emailDestination.getCc() + "\n"
                               + "Bcc: " + emailDestination.getBcc() + "\n"
                               + "\n"
                               + "Are you sure?";

            ConfirmEvent.fire(this, msg, isOk -> {
                if (isOk && !isDisposed()) {
                    restFactory
                            .create(ANALYTIC_RULE_RESOURCE)
                            .call(res -> res.sendTestEmail(emailDestination))
                            .owner(this)
                            .taskMonitorFactory(view.getSendTestEmailTaskMonitor())
                            .exec();
                }
            });
        }
    }

    private boolean validate(final NotificationEmailDestination emailDestination,
                             final ValidationMode... validationModes) {
        final List<String> msgs = new ArrayList<>();
        final String subject = emailDestination.getSubjectTemplate();
        final String body = emailDestination.getBodyTemplate();
        Set<ValidationMode> modeSet = NullSafe.asSet(validationModes);
        if (modeSet.isEmpty()) {
            modeSet = NullSafe.asSet(ValidationMode.values());
        }

        if (modeSet.contains(ValidationMode.SUBJECT)) {
            if (NullSafe.isBlankString(subject)) {
                msgs.add("Subject cannot be blank.");
            }
            if (subject.contains("\n") || subject.contains("\r")) {
                msgs.add("Subject contains line breaks or carriage returns. It must be one line only.");
            }
        }
        if (modeSet.contains(ValidationMode.BODY)) {
            if (NullSafe.isBlankString(body)) {
                msgs.add("Body cannot be blank.");
            }
        }
        if (modeSet.contains(ValidationMode.RECIPIENTS)) {
            final boolean hasRecipients = Stream.of(
                            emailDestination.getTo(),
                            emailDestination.getCc(),
                            emailDestination.getBcc())
                    .anyMatch(str -> !NullSafe.isBlankString(str));

            if (!hasRecipients) {
                msgs.add("You must enter at least one recipient (To, Cc, Bcc).");
            }
        }

        if (!msgs.isEmpty()) {
            final String prefix = msgs.size() > 1
                    ? "The following errors were found with the email notification settings:"
                    : "The following error was found with the email notification settings:";
            final String msg = prefix + "\n\n" + String.join("\n", msgs);

            AlertEvent.fireError(this, msg, null);
            return false;
        }
        return true;
    }



    // --------------------------------------------------------------------------------


    private enum ValidationMode {
        SUBJECT,
        BODY,
        RECIPIENTS
    }
}
