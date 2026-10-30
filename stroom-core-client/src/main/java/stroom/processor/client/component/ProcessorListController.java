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

package stroom.processor.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.data.client.OpenLinkUtil;
import stroom.data.client.component.RefCellsController;
import stroom.dispatch.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterExpressionUtil;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorListRowResultPage;
import stroom.processor.shared.ProcessorResource;
import stroom.processor.shared.ProcessorRow;
import stroom.query.api.ExpressionOperator;
import stroom.util.client.cell.DocRefCell;
import stroom.util.client.cell.UserRefCell;
import stroom.widget.component.client.Controller;
import stroom.widget.tooltip.client.component.InfoTooltipController;
import stroom.widget.util.client.HasRefresh;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// The processors and their filters ([ProcessorList]) of a pipeline, an analytic rule or a folder
/// (or every one): fetched as a tree, their enabled states and priorities saved as they are
/// changed, their info shown in a tooltip. Its owner acts on its buttons.
public class ProcessorListController extends Controller implements HasRefresh {

    private static final ProcessorResource PROCESSOR_RESOURCE = GWT.create(ProcessorResource.class);
    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);

    private final RestDataProvider<ProcessorListRow, ProcessorListRowResultPage> dataProvider;
    private final FetchProcessorRequest request;
    private final RefCellsController refCells;
    private final InfoTooltipController infoTooltip;
    private final ProcessorInfoBuilder processorInfoBuilder;
    private ProcessorList view;
    private ProcessorListRow nextSelection;

    private final RestSaveQueue<Integer, Boolean> processorEnabledSaveQueue;
    private final RestSaveQueue<Integer, Boolean> processorFilterEnabledSaveQueue;
    private final RestSaveQueue<Integer, Integer> processorFilterPrioritySaveQueue;

    private ExpressionOperator expression;
    private boolean initiated;

    /// Made by GIN; [#attach(ProcessorList)] gives it its list.
    ///
    /// @param eventBus             The application's event bus.
    /// @param infoTooltip          Shows a row's info.
    /// @param restFactory          Fetches the processors and saves their changes.
    /// @param processorInfoBuilder Writes a row's info.
    /// @param refCells             Lets the pipeline and user cells open their pipelines and users.
    @Inject
    public ProcessorListController(final EventBus eventBus,
                                   final InfoTooltipController infoTooltip,
                                   final RestFactory restFactory,
                                   final ProcessorInfoBuilder processorInfoBuilder,
                                   final RefCellsController refCells) {
        super(eventBus);
        this.refCells = own(refCells);
        this.infoTooltip = own(infoTooltip);
        this.processorInfoBuilder = processorInfoBuilder;
        this.request = new FetchProcessorRequest();
        own(OpenLinkUtil.addClickHandler(this, infoTooltip.asWidget()));

        this.processorEnabledSaveQueue = new RestSaveQueue<Integer, Boolean>() {
            @Override
            protected void doAction(final Integer key, final Boolean value, final Consumer<Integer> consumer) {
                restFactory
                        .create(PROCESSOR_RESOURCE)
                        .method(res -> res.setEnabled(key, value))
                        .onSuccess(res -> consumer.accept(key))
                        .onFailure(res -> {
                            AlertEvent.fireError(ProcessorListController.this, res.getMessage(), null);
                            consumer.accept(key);
                        })
                        .owner(ProcessorListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            }
        };
        processorFilterEnabledSaveQueue = new RestSaveQueue<Integer, Boolean>() {
            @Override
            protected void doAction(final Integer key, final Boolean value, final Consumer<Integer> consumer) {
                restFactory
                        .create(PROCESSOR_FILTER_RESOURCE)
                        .method(res -> res.setEnabled(key, value))
                        .onSuccess(res -> consumer.accept(key))
                        .onFailure(res -> {
                            AlertEvent.fireError(ProcessorListController.this, res.getMessage(), null);
                            consumer.accept(key);
                        })
                        .owner(ProcessorListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            }
        };
        processorFilterPrioritySaveQueue = new RestSaveQueue<Integer, Integer>() {
            @Override
            protected void doAction(final Integer key, final Integer value, final Consumer<Integer> consumer) {
                restFactory
                        .create(PROCESSOR_FILTER_RESOURCE)
                        .method(res -> res.setPriority(key, value))
                        .onSuccess(res -> consumer.accept(key))
                        .onFailure(res -> {
                            AlertEvent.fireError(ProcessorListController.this, res.getMessage(), null);
                            consumer.accept(key);
                        })
                        .owner(ProcessorListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            }
        };

        dataProvider = new RestDataProvider<ProcessorListRow, ProcessorListRowResultPage>(getEventBus()) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ProcessorListRowResultPage> dataConsumer,
                                final RestErrorHandler errorHandler) {
                request.setExpression(expression);
                restFactory
                        .create(PROCESSOR_FILTER_RESOURCE)
                        .method(res -> res.find(request))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
                        .owner(ProcessorListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            }

            @Override
            protected void changeData(final ProcessorListRowResultPage data) {
                super.changeData(data);
                onChangeData(data);
            }
        };
    }

    /// Attaches this to a list, e.g. one a template declares, which it fills from the server. The list is then this
    /// controller's widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public ProcessorListController attach(final ProcessorList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);

        view.setOnShowInfo((row, popupPosition, anchor) ->
                infoTooltip.show(processorInfoBuilder.get(row), popupPosition, anchor));
        view.setOnExpand((row, expanded) -> {
            request.setRowExpanded(row, expanded);
            refresh();
        });
        view.setOnEnabledChange(this::onEnabledChange);
        view.setOnPriorityChange(this::onPriorityChange);
        return this;
    }

    /// Adds the columns, the first time.
    ///
    /// @param allowUpdate Whether the enabled tick boxes and priorities can be changed.
    public void setAllowUpdate(final boolean allowUpdate) {
        if (!view.hasColumns()) {
            view.addColumns(allowUpdate,
                    refCells.configure(new DocRefCell.Builder<ProcessorListRow>()),
                    refCells.configure(new UserRefCell.Builder<ProcessorListRow>()));

            // Handle use of the expander column.
            dataProvider.setTreeRowHandler(view.createTreeRowHandler(request));
        }
    }

    /// @param actions The actions whose buttons show.
    public void setAvailableActions(final Set<ProcessorAction> actions) {
        view.setAvailableActions(actions);
    }

    /// @param action  An action.
    /// @param enabled Whether its button can be pressed.
    public void setActionEnabled(final ProcessorAction action, final boolean enabled) {
        view.setActionEnabled(action, enabled);
    }

    /// @param onAction Given the action whose button is pressed.
    public void setOnAction(final Consumer<ProcessorAction> onAction) {
        view.setOnAction(onAction);
    }

    /// @return The selection.
    public MultiSelectionModel<ProcessorListRow> getSelectionModel() {
        return view.getSelectionModel();
    }

    @Override
    public void refresh() {
        if (!initiated) {
            initiated = true;
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    /// Shows what belongs to a document, e.g. a feed's or pipeline's.
    ///
    /// @param docRef   The document's reference.
    /// @param document The document, which may be null.
    public void read(final DocRef docRef, final Object document) {
        if (docRef == null) {
            expression = ProcessorFilterExpressionUtil.createBasicExpression();
        } else if (PipelineDoc.TYPE.equals(docRef.getType())) {
            expression = ProcessorFilterExpressionUtil.createPipelineExpression(docRef);
        } else if (AnalyticRuleDoc.TYPE.equals(docRef.getType())) {
            expression = ProcessorFilterExpressionUtil.createAnalyticRuleExpression(docRef);
        } else {
            expression = ProcessorFilterExpressionUtil.createFolderExpression(docRef);
        }

        refresh();
    }

    /// @return What the processors shown match.
    public ExpressionOperator getExpression() {
        return expression;
    }

    /// @param expression What the processors shown are to match, from the next fetch.
    public void setExpression(final ExpressionOperator expression) {
        this.expression = expression;
    }

    /// @param nextSelection The row to select once the next fetch is in, e.g. one just added.
    public void setNextSelection(final ProcessorListRow nextSelection) {
        this.nextSelection = nextSelection;
    }

    private void onChangeData(final ProcessorListRowResultPage data) {
        final MultiSelectionModel<ProcessorListRow> selectionModel = view.getSelectionModel();
        final ProcessorListRow selected = selectionModel.getSelected();

        if (nextSelection != null) {
            for (final ProcessorListRow row : data.getValues()) {
                if (row instanceof ProcessorFilterRow) {
                    if (nextSelection.equals(row)) {
                        selectionModel.setSelected(row);
                        break;
                    }
                }
            }
            nextSelection = null;

        } else if (selected != null) {
            if (!data.getValues().contains(selected)) {
                selectionModel.setSelected(selected, false);
            }
        }
    }

    private void onEnabledChange(final ProcessorListRow row, final boolean enabled) {
        if (row instanceof final ProcessorFilterRow processorFilterRow) {
            final ProcessorFilter processorFilter = processorFilterRow.getProcessorFilter();
            processorFilterRow.setProcessorFilter(processorFilter.copy().enabled(enabled).build());
            processorFilterEnabledSaveQueue.setValue(processorFilter.getId(), enabled);

        } else if (row instanceof final ProcessorRow processorRow) {
            final Processor processor = processorRow.getProcessor();
            processorRow.setProcessor(processor.copy().enabled(enabled).build());
            processorEnabledSaveQueue.setValue(processor.getId(), enabled);
        }
    }

    private void onPriorityChange(final ProcessorListRow row, final int priority) {
        if (row instanceof final ProcessorFilterRow processorFilterRow) {
            final ProcessorFilter processorFilter = processorFilterRow.getProcessorFilter();
            processorFilterRow.setProcessorFilter(processorFilter.copy().priority(priority).build());
            processorFilterPrioritySaveQueue.setValue(processorFilter.getId(), priority);
        }
    }
}
