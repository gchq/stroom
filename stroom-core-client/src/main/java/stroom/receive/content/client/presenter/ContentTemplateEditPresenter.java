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

package stroom.receive.content.client.presenter;

import stroom.data.client.presenter.EditExpressionPresenter;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.item.client.SelectionBox;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;
import stroom.receive.content.client.presenter.ContentTemplateEditPresenter.ContentTemplateEditView;
import stroom.receive.content.shared.ContentTemplate;
import stroom.receive.content.shared.ContentTemplateResource;
import stroom.receive.content.shared.TemplateType;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class ContentTemplateEditPresenter
        extends MyPresenterWidget<ContentTemplateEditView>
        implements Focus {

    private static final ContentTemplateResource CONTENT_TEMPLATE_RESOURCE = GWT.create(
            ContentTemplateResource.class);

    private final EditExpressionPresenter editExpressionPresenter;
    private final SimpleFieldSelectionListModel fieldSelectionBoxModel;
    private final DocSelectionBoxPresenter pipelineSelectionPresenter;
    private ContentTemplate originalTemplate;

    @Inject
    public ContentTemplateEditPresenter(final EventBus eventBus,
                                        final ContentTemplateEditView view,
                                        final DocSelectionBoxPresenter pipelineSelectionPresenter,
                                        final EditExpressionPresenter editExpressionPresenter,
                                        final RestFactory restFactory) {
        super(eventBus, view);
        this.fieldSelectionBoxModel = new SimpleFieldSelectionListModel();
        this.editExpressionPresenter = editExpressionPresenter;
        this.pipelineSelectionPresenter = pipelineSelectionPresenter;
        view.setExpressionView(editExpressionPresenter.getView());

        restFactory
                .create(CONTENT_TEMPLATE_RESOURCE)
                .method(ContentTemplateResource::fetchFields)
                .onSuccess(queryFields -> {
                    fieldSelectionBoxModel.clear();
                    fieldSelectionBoxModel.addItems(queryFields);
                })
                .taskMonitorFactory(this)
                .exec();

        editExpressionPresenter.init(
                restFactory,
                MetaFields.STREAM_STORE_DOC_REF,
                fieldSelectionBoxModel);

        pipelineSelectionPresenter.setIncludedTypes(PipelineDoc.TYPE);
        pipelineSelectionPresenter.setRequiredPermissions(DocumentPermission.VIEW);
        getView().setPipelineSelector(pipelineSelectionPresenter.getView());
    }

    @Override
    protected void onBind() {
        registerHandler(getView().getTemplateTypeSelectionBox().addValueChangeHandler(event -> {
            getView().setTemplateType(event.getValue());
        }));
    }

    @Override
    public void focus() {
        getView().focus();
    }

    void read(final ContentTemplate contentTemplate) {
        this.originalTemplate = contentTemplate;
        getView().setName(contentTemplate.getName());
        editExpressionPresenter.read(NullSafe.requireNonNullElseGet(
                contentTemplate.getExpression(),
                () -> ExpressionOperator.builder().build()));

        final ContentTemplateEditView view = getView();
        view.setName(contentTemplate.getName());
        view.setDescription(contentTemplate.getDescription());
        view.setTemplateType(contentTemplate.getTemplateType());
        view.setCopyDependencies(contentTemplate.isCopyElementDependencies());
        view.setProcessorPriority(contentTemplate.getProcessorPriority());
        view.setProcessorMaxConcurrent(contentTemplate.getProcessorMaxConcurrent());
        pipelineSelectionPresenter.setSelectedEntityReference(contentTemplate.getPipeline(), true);
    }

    ContentTemplate write() {
        final ExpressionOperator expression = editExpressionPresenter.write();
        final ContentTemplateEditView view = getView();
        return ContentTemplate.copy(originalTemplate)
                .withName(view.getName())
                .withDescription(view.getDescription())
                .withTemplateType(view.getTemplateType())
                .withCopyElementDependencies(view.isCopyDependencies())
                .withPipeline(pipelineSelectionPresenter.getSelectedEntityReference())
                .withExpression(expression)
                .withProcessorPriority(view.getProcessorPriority())
                .withProcessorMaxConcurrent(view.getProcessorMaxConcurrent())
                .build();
    }

    @Override
    public synchronized void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        super.setTaskMonitorFactory(taskMonitorFactory);
        fieldSelectionBoxModel.setTaskMonitorFactory(taskMonitorFactory);
    }


    // --------------------------------------------------------------------------------


    public interface ContentTemplateEditView extends View {

        void focus();

        void setExpressionView(View view);

        String getName();

        void setName(String name);

        String getDescription();

        void setDescription(String description);

        SelectionBox<TemplateType> getTemplateTypeSelectionBox();

        TemplateType getTemplateType();

        void setTemplateType(final TemplateType templateType);

        boolean isCopyDependencies();

        void setCopyDependencies(final boolean copyDependencies);

        void setPipelineSelector(final View view);

        int getProcessorPriority();

        void setProcessorPriority(int value);

        int getProcessorMaxConcurrent();

        void setProcessorMaxConcurrent(int value);
    }
}
