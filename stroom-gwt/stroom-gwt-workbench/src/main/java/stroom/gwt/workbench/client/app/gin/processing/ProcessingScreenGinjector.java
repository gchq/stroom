/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.gin.processing;

import stroom.analytics.client.component.ExecutionSchedulesController;
import stroom.dashboard.client.component.ProcessorLimitsController;
import stroom.data.client.component.CreateProcessorsController;
import stroom.data.client.component.DataPreviewTabController;
import stroom.data.client.component.DataUploadController;
import stroom.data.client.component.MetaBrowserController;
import stroom.data.client.component.ProcessorTasksListController;
import stroom.data.client.component.ProcessorTasksTabController;
import stroom.data.client.component.SourceTabController;
import stroom.document.client.DocumentPluginEventManager;
import stroom.explorer.client.component.DocumentChooserController;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.node.client.component.NodeGroupEditorController;
import stroom.node.client.component.NodeGroupsController;
import stroom.pipeline.client.PipelinePlugin;
import stroom.pipeline.client.XsltPlugin;
import stroom.pipeline.client.component.PipelineEditor;
import stroom.pipeline.client.component.SaveChangedDocumentsController;
import stroom.pipeline.stepping.client.component.SteppingFilterEditorController;
import stroom.pipeline.stepping.client.component.SteppingScreenController;
import stroom.pipeline.structure.client.PipelineElementTypesFactory;
import stroom.pipeline.structure.client.PipelineModelFactory;
import stroom.pipeline.structure.client.component.PipelineTree;
import stroom.processor.client.component.ProcessorEditorController;
import stroom.processor.client.component.ProcessorProfilesController;
import stroom.processor.client.component.ProcessorsController;
import stroom.processor.task.client.component.ProcessorFilterTasksController;
import stroom.receive.rules.client.component.DataRetentionController;
import stroom.receive.rules.client.component.RuleSetTabController;

import com.google.gwt.inject.client.GinModules;

/// A [ScreenGinjector] for the 'processing' screens (node groups, processors and their filters,
/// processor profiles, pipelines and stepping, data retention and receipt rules, execution
/// schedules and the data browser), which creates their controllers and components as Stroom's GIN
/// modules do. Create one each time the story renders and give it to the harness with
/// `ScreenHarness.builder(...).injector(...)`.
///
/// It lists the shared base modules of `AppScreenGinjector` that these screens need, plus mirrors
/// of Stroom's modules in this package.
@GinModules({
        ProcessingExtrasScreenModule.class,
})
public interface ProcessingScreenGinjector extends ScreenGinjector {

    // Node groups

    /// @return The 'Node Groups' tab, as `NodeGroupsPlugin` opens it.
    NodeGroupsController getNodeGroupsController();

    /// @return The node group members dialog ('Edit Node Group - name').
    NodeGroupEditorController getNodeGroupEditorController();

    // Execution schedules

    /// @return The 'Execution Schedule Manager' tab, as `ExecutionSchedulesPlugin` opens it.
    ExecutionSchedulesController getExecutionSchedulesController();

    // Processors

    /// @return The 'Processor Profiles' tab, as `ProcessorProfilesPlugin` opens it.
    ProcessorProfilesController getProcessorProfilesController();

    /// @return A pipeline's 'Processors' tab (the processor and filter tree).
    ProcessorsController getProcessors();

    /// @return The 'Add Filter'/'Edit Filter' dialog, as `ProcessorsController` shows it.
    ProcessorEditorController getProcessorEditorController();

    /// @return The 'Process Search Results' dialog, as `QueryPaneController` shows it.
    ProcessorLimitsController getProcessorLimitsController();

    /// @return The task list in the lower pane of a folder's 'Active Tasks' tab.
    ProcessorTasksListController getProcessorTasksListController();

    /// @return A folder's, feed's or pipeline's 'Active Tasks' tab (the task summary above the task
    /// list), as `FolderTabs`, `FeedEditor` and `PipelineEditor` create it.
    ProcessorTasksTabController getProcessorTasksTabController();

    /// @return A processor filter's 'Tasks' tab, as `ProcessorTaskPlugin` opens it.
    ProcessorFilterTasksController getProcessorFilterTasksController();

    // Data

    /// @return The stream browser of a 'Data' tab (streams, relations and the data preview).
    MetaBrowserController getMetaBrowser();

    /// @return A 'Data Preview' tab, as `DataPreviewTabPlugin` opens it.
    DataPreviewTabController getDataPreviewTabController();

    /// @return A 'Source' tab, as `SourceTabPlugin` opens it.
    SourceTabController getSourceTabController();

    /// @return The 'Create Processors' dialog, as the data browser's meta list shows it.
    CreateProcessorsController getCreateProcessorsController();

    /// @return The dialog that asks which changed documents to save, as `PipelinePlugin` shows it.
    SaveChangedDocumentsController getSaveChangedDocumentsController();

    /// @return A feed's 'Upload' dialog, as the data browser shows it.
    DataUploadController getDataUploadController();

    // Pipelines and stepping

    /// @return A Pipeline's editor, as `PipelinePlugin` opens it.
    PipelineEditor getPipelineEditor();

    /// @return A pipeline's stepper, as `PipelineEditor` shows it in stepping mode.
    SteppingScreenController getSteppingScreen();

    /// @return A document chooser, e.g. 'Choose Pipeline To Step With' (`PipelinePlugin`).
    DocumentChooserController getDocumentChooserController();

    /// @return The 'Change Step Filters' dialog, as `SteppingScreenController` shows it.
    SteppingFilterEditorController getSteppingFilterEditorController();

    /// @return A pipeline's element tree, as `PipelineStructureController` shows it.
    PipelineTree getPipelineTree();

    /// @return The cache of the pipeline element types (`GET /pipeline/v1/propertyTypes`).
    PipelineElementTypesFactory getPipelineElementTypesFactory();

    /// @return The factory of a pipeline's model (`POST /pipeline/v1/fetchPipelineLayers`).
    PipelineModelFactory getPipelineModelFactory();

    // Document plugins (getting a plugin registers it for its document type, see
    // `StoryDocumentPlugins`)

    /// @return Stroom's handler of the document events (open, save, close...), which finds each
    /// document's plugin in the `DocumentPluginRegistry`.
    DocumentPluginEventManager getDocumentPluginEventManager();

    /// @return The Pipeline document plugin (opening, saving with the 'Save Pipeline' picker, stepping).
    PipelinePlugin getPipelinePlugin();

    /// @return The XSLT document plugin, which loads and saves a stepping element's code.
    XsltPlugin getXsltPlugin();

    // Data retention and receipt rules

    /// @return The 'Data Retention' tab, as `DataRetentionPlugin` opens it.
    DataRetentionController getDataRetentionController();

    /// @return The 'Data Receipt Rules' tab, as `ReceiveDataRuleSetPlugin` opens it.
    RuleSetTabController getRuleSetTabController();
}
