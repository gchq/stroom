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

import stroom.analytics.client.presenter.ExecutionScheduleManager;
import stroom.dashboard.client.query.ProcessorLimitsPresenter;
import stroom.data.client.presenter.DataPreviewTabPresenter;
import stroom.data.client.presenter.DataUploadPresenter;
import stroom.data.client.presenter.MetaPresenter;
import stroom.data.client.presenter.ProcessChoicePresenter;
import stroom.data.client.presenter.SourceTabPresenter;
import stroom.document.client.DocumentPluginEventManager;
import stroom.explorer.client.presenter.DocSelectionPopup;
import stroom.gwt.workbench.client.app.gin.EntityScreenModule;
import stroom.gwt.workbench.client.app.gin.ExplorerScreenModule;
import stroom.gwt.workbench.client.app.gin.MonitoringScreenModule;
import stroom.gwt.workbench.client.app.gin.ScreenViewsModule;
import stroom.gwt.workbench.client.app.gin.SecurityScreenModule;
import stroom.gwt.workbench.client.app.gin.TaskScreenModule;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.node.client.presenter.NodeGroupEditPresenter;
import stroom.node.client.presenter.NodeGroupPresenter;
import stroom.pipeline.client.PipelinePlugin;
import stroom.pipeline.client.XsltPlugin;
import stroom.pipeline.client.presenter.PipelinePresenter;
import stroom.pipeline.stepping.client.presenter.SteppingFilterPresenter;
import stroom.pipeline.stepping.client.presenter.SteppingPresenter;
import stroom.pipeline.structure.client.presenter.PipelineElementTypesFactory;
import stroom.pipeline.structure.client.presenter.PipelineModelFactory;
import stroom.pipeline.structure.client.presenter.PipelineTreePresenter;
import stroom.processor.client.presenter.ProcessorEditPresenter;
import stroom.processor.client.presenter.ProcessorPresenter;
import stroom.processor.client.presenter.ProcessorProfilePresenter;
import stroom.receive.rules.client.presenter.DataRetentionPresenter;
import stroom.receive.rules.client.presenter.RuleSetPresenter;

import com.google.gwt.inject.client.GinModules;

/// A [ScreenGinjector] for the 'processing' screens (node groups, processors and their filters,
/// processor profiles, pipelines and stepping, data retention and receipt rules, execution
/// schedules and the data browser), which creates their presenters and views as Stroom's GIN
/// modules do. Create one each time the story renders and give it to the harness with
/// `ScreenHarness.builder(...).injector(...)`.
///
/// It lists the shared base modules of `AppScreenGinjector` that these screens need, plus mirrors
/// of Stroom's modules in this package.
@GinModules({
        ScreenViewsModule.class,
        EntityScreenModule.class,
        ExplorerScreenModule.class,
        MonitoringScreenModule.class,
        SecurityScreenModule.class,
        TaskScreenModule.class,
        PipelineScreenModule.class,
        PolicyScreenModule.class,
        StreamStoreScreenModule.class,
        ProcessingExtrasScreenModule.class,
        AnalyticsScreenModule.class,
})
public interface ProcessingScreenGinjector extends ScreenGinjector {

    // Node groups

    /// @return The 'Node Groups' tab, as `NodeGroupsPlugin` opens it.
    NodeGroupPresenter getNodeGroupPresenter();

    /// @return The node group members dialog ('Edit Node Group - name').
    NodeGroupEditPresenter getNodeGroupEditPresenter();

    // Execution schedules

    /// @return The 'Execution Schedule Manager' tab, as `ExecutionScheduleManagerPlugin` opens it.
    ExecutionScheduleManager getExecutionScheduleManager();

    // Processors

    /// @return The 'Processor Profiles' tab, as `ProcessorProfilePlugin` opens it.
    ProcessorProfilePresenter getProcessorProfilePresenter();

    /// @return A pipeline's 'Processors' tab (the processor and filter tree).
    ProcessorPresenter getProcessorPresenter();

    /// @return The 'Add Filter'/'Edit Filter' dialog, as `ProcessorPresenter` shows it.
    ProcessorEditPresenter getProcessorEditPresenter();

    /// @return The 'Process Search Results' dialog, as `QueryPresenter` shows it.
    ProcessorLimitsPresenter getProcessorLimitsPresenter();

    // Data

    /// @return The stream browser of a 'Data' tab (streams, relations and the data preview).
    MetaPresenter getMetaPresenter();

    /// @return A 'Data Preview' tab, as `DataPreviewTabPlugin` opens it.
    DataPreviewTabPresenter getDataPreviewTabPresenter();

    /// @return A 'Source' tab, as `SourceTabPlugin` opens it.
    SourceTabPresenter getSourceTabPresenter();

    /// @return The 'Create Processors' dialog, as the data browser's meta list shows it.
    ProcessChoicePresenter getProcessChoicePresenter();

    /// @return A feed's 'Upload' dialog, as the data browser shows it.
    DataUploadPresenter getDataUploadPresenter();

    // Pipelines and stepping

    /// @return A Pipeline's editor tab, as `PipelinePlugin` creates it.
    PipelinePresenter getPipelinePresenter();

    /// @return A pipeline's stepper, as `PipelinePresenter` shows it in stepping mode.
    SteppingPresenter getSteppingPresenter();

    /// @return A document chooser popup, e.g. 'Choose Pipeline To Step With' (`PipelinePlugin`).
    DocSelectionPopup getDocSelectionPopup();

    /// @return The 'Change Step Filters' dialog, as `SteppingPresenter` shows it.
    SteppingFilterPresenter getSteppingFilterPresenter();

    /// @return A pipeline's element tree, as `PipelineStructurePresenter` shows it.
    PipelineTreePresenter getPipelineTreePresenter();

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
    DataRetentionPresenter getDataRetentionPresenter();

    /// @return The 'Data Receipt Rules' tab, as `ReceiveDataRuleSetPlugin` opens it.
    RuleSetPresenter getRuleSetPresenter();
}
