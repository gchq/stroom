/*
 * Copyright 2025 Crown Copyright
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

package stroom.receive.content.client.component;

import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.component.DocSelectionController;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.component.ExpressionEditorController;
import stroom.query.client.model.SimpleFieldSelectionListModel;
import stroom.receive.content.shared.ContentTemplate;
import stroom.receive.content.shared.ContentTemplateResource;
import stroom.security.shared.DocumentPermission;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Predicate;

/// Adds or edits a content template in a dialog ([ContentTemplateEditor]), made for one use.
public class ContentTemplateEditorController extends Controller {

    private static final ContentTemplateResource CONTENT_TEMPLATE_RESOURCE = GWT.create(
            ContentTemplateResource.class);

    private final ContentTemplateEditor view;
    private final ExpressionEditorController expressionEditor;
    private final DocSelectionController pipelineSelection;
    private ContentTemplate originalTemplate;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                   The application's event bus.
    /// @param view                       The content.
    /// @param pipelineSelectionProvider  Makes the pipeline picker's controller.
    /// @param expressionEditor           Edits the expression choosing the template's data.
    /// @param restFactory                Fetches the fields the expression may use.
    @Inject
    public ContentTemplateEditorController(final EventBus eventBus,
                                           final ContentTemplateEditor view,
                                           final Provider<DocSelectionController> pipelineSelectionProvider,
                                           final ExpressionEditorController expressionEditor,
                                           final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.expressionEditor = own(expressionEditor).attach(view.getExpressionEditor());

        final SimpleFieldSelectionListModel fieldSelectionBoxModel = new SimpleFieldSelectionListModel();
        // Shows its requests' progress as this controller's context does, once it is shown
        fieldSelectionBoxModel.setTaskMonitorFactory(() -> taskMonitor().createTaskMonitor());
        restFactory
                .create(CONTENT_TEMPLATE_RESOURCE)
                .method(ContentTemplateResource::fetchFields)
                .onSuccess(queryFields -> {
                    fieldSelectionBoxModel.clear();
                    fieldSelectionBoxModel.addItems(queryFields);
                })
                .owner(this)
                .taskMonitorFactory(() -> taskMonitor().createTaskMonitor())
                .exec();
        expressionEditor.init(MetaFields.STREAM_STORE_DOC_REF, fieldSelectionBoxModel);

        pipelineSelection = make(pipelineSelectionProvider);
        pipelineSelection.attach(view.getPipelineBox());
        pipelineSelection.setIncludedTypes(PipelineDoc.TYPE);
        pipelineSelection.setRequiredPermissions(DocumentPermission.VIEW);
    }

    /// Shows a template as a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener   What shows it.
    /// @param caption  The dialog's caption, e.g. `Add New Template`.
    /// @param template The template to edit, or a new one.
    /// @param onOk     Given the template as edited when OK is pressed; returns whether it is kept,
    ///                 when the dialog closes, or not (having said why), when it stays open.
    public void edit(final HasControllerNode opener,
                     final String caption,
                     final ContentTemplate template,
                     final Predicate<ContentTemplate> onOk) {
        read(template);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(1000, 800))
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (!e.isOk() || onOk.test(write())) {
                        e.hide();
                    } else {
                        e.reset();
                    }
                })
                .fire();
    }

    private void read(final ContentTemplate contentTemplate) {
        this.originalTemplate = contentTemplate;
        expressionEditor.read(Objects.requireNonNullElseGet(contentTemplate.getExpression(),
                () -> ExpressionOperator.builder().build()));
        view.setName(contentTemplate.getName());
        view.setDescription(contentTemplate.getDescription());
        view.setTemplateType(contentTemplate.getTemplateType());
        view.setCopyDependencies(contentTemplate.isCopyElementDependencies());
        view.setProcessorPriority(contentTemplate.getProcessorPriority());
        view.setProcessorMaxConcurrent(contentTemplate.getProcessorMaxConcurrent());
        pipelineSelection.setSelectedEntityReference(contentTemplate.getPipeline(), true);
    }

    private ContentTemplate write() {
        final ExpressionOperator expression = expressionEditor.write();
        return ContentTemplate.copy(originalTemplate)
                .withName(view.getName())
                .withDescription(view.getDescription())
                .withTemplateType(view.getTemplateType())
                .withCopyElementDependencies(view.isCopyDependencies())
                .withPipeline(pipelineSelection.getSelectedEntityReference())
                .withExpression(expression)
                .withProcessorPriority(view.getProcessorPriority())
                .withProcessorMaxConcurrent(view.getProcessorMaxConcurrent())
                .build();
    }

}
