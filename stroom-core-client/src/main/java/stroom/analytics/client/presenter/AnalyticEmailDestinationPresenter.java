/*
 * Copyright 2022 Crown Copyright
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
 *
 */

package stroom.analytics.client.presenter;

import stroom.analytics.client.presenter.AnalyticEmailDestinationPresenter.AnalyticEmailDestinationView;
import stroom.analytics.shared.AnalyticNotificationEmailDestination;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.ui.config.client.UiConfigCache;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

public class AnalyticEmailDestinationPresenter
        extends MyPresenterWidget<AnalyticEmailDestinationView>
        implements DirtyUiHandlers, HasDirtyHandlers {

    private final EditorPresenter subjectEditorPresenter;
    private final EditorPresenter bodyEditorPresenter;
    private final UiConfigCache uiConfigCache;

    @Inject
    public AnalyticEmailDestinationPresenter(final EventBus eventBus,
                                             final AnalyticEmailDestinationView view,
                                             final EditorPresenter subjectEditorPresenter,
                                             final EditorPresenter bodyEditorPresenter,
                                             final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.subjectEditorPresenter = subjectEditorPresenter;
        this.bodyEditorPresenter = bodyEditorPresenter;
        this.uiConfigCache = uiConfigCache;
        initEditor(this.subjectEditorPresenter);
        initEditor(this.bodyEditorPresenter);
        view.setUiHandlers(this);
        view.setSubjectTemplateEditorView(subjectEditorPresenter.getView());
        view.setBodyTemplateEditorView(bodyEditorPresenter.getView());

    }

    private void initEditor(final EditorPresenter editorPresenter) {
        // TODO Make a jinja mode
        editorPresenter.setMode(AceEditorMode.TEXT);
        editorPresenter.getLineNumbersOption().setOff();
        editorPresenter.getViewAsHexOption().setOff();
        editorPresenter.getViewAsHexOption().setUnavailable();
    }

    public void read(final AnalyticNotificationEmailDestination destination) {
        uiConfigCache.get()
                .onSuccess(extendedUiConfig -> {
                    if (destination != null) {
                        getView().setTo(destination.getTo());
                        getView().setCc(destination.getCc());
                        getView().setBcc(destination.getBcc());
                        subjectEditorPresenter.setText(destination.getSubjectTemplate());
                        bodyEditorPresenter.setText(destination.getBodyTemplate());
                    } else {
                        subjectEditorPresenter.setText(extendedUiConfig.getDefaultSubjectTemplate());
                        bodyEditorPresenter.setText(extendedUiConfig.getDefaultBodyTemplate());
                    }
                });
    }

    public AnalyticNotificationEmailDestination write() {
        return AnalyticNotificationEmailDestination
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

//        String getSubjectTemplate();
//
//        void setSubjectTemplate();
//
//        String getBodyTemplate();
//
//        void setBodyTemplate();

        void setSubjectTemplateEditorView(View view);

        void setBodyTemplateEditorView(View view);
    }
}
