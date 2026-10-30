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

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AnalyticProcessConfig;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.docref.DocRef;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/// An analytic rule's or report's Execution tab ([AnalyticProcessing]): how the rule runs, chosen
/// from the processing types its document offers ([#setProcessingTypes]), and that type's settings:
/// the execution schedules, the processors and their filters, or the table builder's settings.
public class AnalyticProcessingController extends Controller {

    private final AnalyticProcessing view;
    private final ScheduledProcessingController scheduledProcessing;
    private final TableBuilderProcessingController tableBuilderProcessing;
    private final StreamingProcessingController streamingProcessing;
    private final UiConfigCache uiConfigCache;

    private AbstractAnalyticRuleDoc document;
    private Consumer<AnalyticProcessType> onProcessingTypeChange = processingType -> {
    };

    /// Made by GIN, with its content and its parts.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   The content.
    /// @param scheduledProcessing    The execution schedules, for a scheduled query.
    /// @param tableBuilderProcessing The table builder's settings.
    /// @param streamingProcessing    The processors and their filters, for a streaming rule.
    /// @param uiConfigCache          Read before the settings are shown.
    @Inject
    public AnalyticProcessingController(final EventBus eventBus,
                                        final AnalyticProcessing view,
                                        final ScheduledProcessingController scheduledProcessing,
                                        final TableBuilderProcessingController tableBuilderProcessing,
                                        final StreamingProcessingController streamingProcessing,
                                        final UiConfigCache uiConfigCache) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        // Read only as its document is
        watch(Contexts.READ_ONLY, view::setReadOnly);
        own(scheduledProcessing);
        own(tableBuilderProcessing);
        own(streamingProcessing);
        this.uiConfigCache = uiConfigCache;
        this.scheduledProcessing = scheduledProcessing;
        this.tableBuilderProcessing = tableBuilderProcessing;
        this.streamingProcessing = streamingProcessing;

        tableBuilderProcessing.setOnChange(this::markChanged);
        view.setOnProcessingTypeChange(processingType -> {
            markChanged();
            setProcessType(processingType);
            onProcessingTypeChange.accept(processingType);
        });
    }

    /// @param processingTypes The processing types the document can use.
    public void setProcessingTypes(final AnalyticProcessType... processingTypes) {
        view.setProcessingTypes(Arrays.asList(processingTypes));
    }

    /// @param isDocumentDirty Whether the document has unsaved changes, which must be saved before an
    ///                        execution schedule or a processor filter is added.
    public void setIsDocumentDirty(final BooleanSupplier isDocumentDirty) {
        scheduledProcessing.setIsDocumentDirty(isDocumentDirty);
        streamingProcessing.setIsDocumentDirty(isDocumentDirty);
    }

    /// @param onProcessingTypeChange Given the processing type when the user changes it.
    public void setOnProcessingTypeChange(final Consumer<AnalyticProcessType> onProcessingTypeChange) {
        this.onProcessingTypeChange = onProcessingTypeChange;
    }

    /// Shows how a rule or report runs.
    ///
    /// @param docRef   The rule or report.
    /// @param document The rule or report.
    public void read(final DocRef docRef, final AbstractAnalyticRuleDoc document) {
        this.document = document;
        uiConfigCache.get(guard(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                final AnalyticProcessConfig analyticProcessConfig = document.getAnalyticProcessConfig();
                final AnalyticProcessType analyticProcessType = Objects.requireNonNullElse(
                        document.getAnalyticProcessType(),
                        AnalyticProcessType.SCHEDULED_QUERY);
                setProcessType(analyticProcessType);

                if (AnalyticProcessType.SCHEDULED_QUERY.equals(analyticProcessType)) {
                    scheduledProcessing
                            .read(docRef);
                } else if (AnalyticProcessType.STREAMING.equals(analyticProcessType)) {
                    streamingProcessing
                            .update(document, document.getQuery());
                } else if (analyticProcessConfig instanceof TableBuilderAnalyticProcessConfig) {
                    //noinspection PatternVariableCanBeUsed // Not in GWT
                    final TableBuilderAnalyticProcessConfig ac =
                            (TableBuilderAnalyticProcessConfig) analyticProcessConfig;
                    tableBuilderProcessing.read(docRef, ac);

                }
            }
        }), taskMonitor());
    }

    /// @return The processing type chosen.
    public AnalyticProcessType getProcessingType() {
        return view.getProcessingType();
    }

    /// @return The chosen processing type's settings, if the document holds them (only the table
    ///         builder's), else `null`.
    public AnalyticProcessConfig writeProcessConfig() {
        AnalyticProcessConfig analyticProcessConfig = null;
        switch (view.getProcessingType()) {
            case STREAMING:
                break;
            case TABLE_BUILDER:
                analyticProcessConfig = tableBuilderProcessing.write();
                break;
            case SCHEDULED_QUERY:
                break;
        }
        return analyticProcessConfig;
    }

    private void setProcessType(final AnalyticProcessType analyticProcessType) {
        switch (analyticProcessType) {
            case STREAMING: {
                streamingProcessing.update(document, document.getQuery());
                view.setProcessSettings(streamingProcessing);
                break;
            }
            case SCHEDULED_QUERY: {
                scheduledProcessing.read(document.asDocRef());
                view.setProcessSettings(scheduledProcessing);
                break;
            }
            case TABLE_BUILDER: {
                view.setProcessSettings(tableBuilderProcessing);
                break;
            }
        }

        view.setProcessingType(analyticProcessType);
    }

}
