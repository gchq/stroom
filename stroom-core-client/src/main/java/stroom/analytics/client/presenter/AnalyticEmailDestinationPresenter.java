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

package stroom.analytics.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.analytics.client.presenter.AnalyticEmailDestinationPresenter.AnalyticEmailDestinationView;
import stroom.analytics.shared.AnalyticRuleResource;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.dispatch.client.RestFactory;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.StringWrapper;
import stroom.widget.button.client.Button;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

public class AnalyticEmailDestinationPresenter
        extends MyPresenterWidget<AnalyticEmailDestinationView>
        implements DirtyUiHandlers, HasDirtyHandlers {

    // TODO Make a jinja mode
    private static final AceEditorMode ACE_EDITOR_MODE = AceEditorMode.TEXT;
    private static final AnalyticRuleResource ANALYTIC_RULE_RESOURCE = GWT.create(AnalyticRuleResource.class);

    private final EditorPresenter subjectEditorPresenter;
    private final EditorPresenter bodyEditorPresenter;
    private final RestFactory restFactory;

    @Inject
    public AnalyticEmailDestinationPresenter(final EventBus eventBus,
                                             final AnalyticEmailDestinationView view,
                                             final EditorPresenter subjectEditorPresenter,
                                             final EditorPresenter bodyEditorPresenter,
                                             final RestFactory restFactory) {
        super(eventBus, view);
        this.subjectEditorPresenter = subjectEditorPresenter;
        this.bodyEditorPresenter = bodyEditorPresenter;
        this.restFactory = restFactory;
        initEditor(this.subjectEditorPresenter);
        initEditor(this.bodyEditorPresenter);
        view.setUiHandlers(this);
        view.setSubjectTemplateEditorView(subjectEditorPresenter.getView());
        view.setBodyTemplateEditorView(bodyEditorPresenter.getView());

        getView().getSendTestEmailBtn().addClickHandler(this::onSendTestEmailClicked);
        getView().getTestSubjectTemplateBtn().addClickHandler(event -> {
            testTemplate(NotificationEmailDestination::getSubjectTemplate, ValidationMode.SUBJECT);
        });
        getView().getTestBodyTemplateBtn().addClickHandler(event -> {
            testTemplate(NotificationEmailDestination::getBodyTemplate, ValidationMode.BODY);
        });
    }

//    private void initSubjectEditor(final EditorPresenter editorPresenter) {
//        editorPresenter.setMode(ACE_EDITOR_MODE);
//        editorPresenter.get
//        editorPresenter.getIndicatorsOption().setOff();
//        editorPresenter.getLineWrapOption().setOff();
//        editorPresenter.getLineNumbersOption().setOff();
//        editorPresenter.getViewAsHexOption().setOff();
//        editorPresenter.getViewAsHexOption().setUnavailable();
//    }

    public NotificationEmailDestination getCurrentEmailDestination() {
        return new NotificationEmailDestination(
                getView().getTo(),
                getView().getCc(),
                getView().getBcc(),
                subjectEditorPresenter.getText(),
                bodyEditorPresenter.getText());
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
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    private void onSendTestEmailClicked(final ClickEvent event) {
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
                if (isOk) {
                    restFactory
                            .create(ANALYTIC_RULE_RESOURCE)
                            .call(res -> res.sendTestEmail(emailDestination))
                            .taskMonitorFactory(getView().getSendTestEmailBtn())
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

    private void initEditor(final EditorPresenter editorPresenter) {
        editorPresenter.setMode(ACE_EDITOR_MODE);
        editorPresenter.getIndicatorsOption().setOff();
        editorPresenter.getIndicatorsOption().setUnavailable();
        editorPresenter.getLineWrapOption().setOn();
        editorPresenter.getLineNumbersOption().setOff();
        editorPresenter.getViewAsHexOption().setOff();
        editorPresenter.getViewAsHexOption().setUnavailable();
        editorPresenter.getFormatAction().setUnavailable();

        editorPresenter.addValueChangeHandler(event -> onDirty());
    }

    public void read(final NotificationEmailDestination destination) {
//        uiConfigCache.get()
//                .onSuccess(extendedUiConfig -> {
        if (destination != null) {
            getView().setTo(destination.getTo());
            getView().setCc(destination.getCc());
            getView().setBcc(destination.getBcc());
            subjectEditorPresenter.setText(destination.getSubjectTemplate());
            bodyEditorPresenter.setText(destination.getBodyTemplate());
        }
//                    } else {
//                        subjectEditorPresenter.setText(extendedUiConfig.getDefaultSubjectTemplate());
//                        bodyEditorPresenter.setText(extendedUiConfig.getDefaultBodyTemplate());
//                });
    }

    public NotificationEmailDestination write() {
        return NotificationEmailDestination
                .builder()
                .to(getView().getTo())
                .cc(getView().getCc())
                .bcc(getView().getBcc())
                .subjectTemplate(subjectEditorPresenter.getText())
                .bodyTemplate(bodyEditorPresenter.getText())
                .build();
    }

    @Override
    public void onDirty() {
        DirtyEvent.fire(this, true);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    // --------------------------------------------------------------------------------


    public interface AnalyticEmailDestinationView extends View, HasUiHandlers<DirtyUiHandlers> {

        String getTo();

        void setTo(String to);

        String getCc();

        void setCc(String cc);

        String getBcc();

        void setBcc(String bcc);

        void setSubjectTemplateEditorView(View view);

        void setBodyTemplateEditorView(View view);

        Button getSendTestEmailBtn();

        Button getTestSubjectTemplateBtn();

        Button getTestBodyTemplateBtn();
    }


    // --------------------------------------------------------------------------------


    private enum ValidationMode {
        SUBJECT,
        BODY,
        RECIPIENTS
    }
}
