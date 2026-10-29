/*
 * Copyright 2018 Crown Copyright
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
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.meta.shared.MetaFields;
import stroom.processor.client.ProcessorProfileClient;
import stroom.processor.client.ProcessorProfileListModel;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.FeedDependencies;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.DateTimeSettingsFactory;
import stroom.query.client.SimpleFieldSelectionListModel;
import stroom.query.client.component.ExpressionEditorController;
import stroom.query.shared.ExpressionResource;
import stroom.query.shared.ValidateExpressionRequest;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.component.UserRefSelectionController;
import stroom.security.shared.FindUserContext;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// The Add Filter and Edit Filter dialog ([ProcessorEdit]): edits a processor filter, or makes a
/// new one, checking its expression and asking before it processes every feed or stream type.
/// Made for one use.
public class ProcessorEditController extends Controller {

    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);
    private static final ExpressionResource EXPRESSION_RESOURCE = GWT.create(ExpressionResource.class);

    private final ProcessorEdit view;
    private final ExpressionEditorController expressionEditor;
    private final RestFactory restFactory;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;
    private final UserRefSelectionController runAsUser;
    private final ClientSecurityContext clientSecurityContext;
    private final Provider<FeedDependenciesEditorController> feedDependenciesEditorProvider;

    private ProcessorType processorType;
    private DocRef pipelineRef;
    private FeedDependencies feedDependencies;
    private Consumer<ProcessorFilter> consumer;
    // What the dialog shows: the filter (or null for a new one) and its query
    private ProcessorFilter filter;
    private boolean existingFilter;
    private QueryData queryData;
    private List<QueryField> fields;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                       The application's event bus.
    /// @param view                           The content.
    /// @param expressionEditor               Edits the expression.
    /// @param restFactory                    Checks the expression, and saves the filter.
    /// @param dateTimeSettingsFactory        Gives the user's date and time settings, to check the
    ///                                       expression.
    /// @param runAsUserProvider              Makes the Run As User picker's controller.
    /// @param clientSecurityContext          Says who the user is, a new filter's default Run As User.
    /// @param feedDependenciesEditorProvider Makes the Set Feed Dependencies dialog.
    /// @param processorProfileClient         Finds the processing profiles.
    /// @param dateTimeBoxControllerProvider  Makes the create time boxes' controllers.
    @Inject
    public ProcessorEditController(final EventBus eventBus,
                                   final ProcessorEdit view,
                                   final ExpressionEditorController expressionEditor,
                                   final RestFactory restFactory,
                                   final DateTimeSettingsFactory dateTimeSettingsFactory,
                                   final Provider<UserRefSelectionController> runAsUserProvider,
                                   final ClientSecurityContext clientSecurityContext,
                                   final Provider<FeedDependenciesEditorController> feedDependenciesEditorProvider,
                                   final ProcessorProfileClient processorProfileClient,
                                   final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.expressionEditor = own(expressionEditor).attach(view.getExpressionEditor());
        make(dateTimeBoxControllerProvider).attach(view.getMinMetaCreateTimeBox());
        make(dateTimeBoxControllerProvider).attach(view.getMaxMetaCreateTimeBox());

        this.restFactory = restFactory;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        this.clientSecurityContext = clientSecurityContext;
        this.feedDependenciesEditorProvider = feedDependenciesEditorProvider;
        view.setOnEditFeedDependencies(this::onEditFeedDependencies);
        runAsUser = make(runAsUserProvider);
        runAsUser.attach(view.getRunAsUserBox());
        runAsUser.setContext(FindUserContext.RUN_AS);

        final ProcessorProfileListModel processorProfileListModel =
                new ProcessorProfileListModel(eventBus, processorProfileClient);
        processorProfileListModel.setTaskMonitorFactory(taskMonitor());
        view.setProfileModel(processorProfileListModel);
    }

    private void read(final ExpressionOperator expression,
                      final DocRef dataSource,
                      final List<QueryField> fields,
                      final Long minMetaCreateTimeMs,
                      final Long maxMetaCreateTimeMs,
                      final Integer maxProcessingTasks,
                      final String profileName,
                      final SimpleDuration maxTaskCreationDelay,
                      final boolean export) {

        final SimpleFieldSelectionListModel selectionBoxModel = new SimpleFieldSelectionListModel();
        selectionBoxModel.addItems(fields);
        expressionEditor.init(dataSource, selectionBoxModel);
        expressionEditor.read(Objects.requireNonNullElse(expression, ExpressionOperator.builder().build()));

        view.setMinMetaCreateTimeMs(minMetaCreateTimeMs);
        view.setMaxMetaCreateTimeMs(maxMetaCreateTimeMs);
        view.setMaxProcessingTasks(maxProcessingTasks);
        view.setProfile(profileName);
        view.setMaxTaskCreationDelay(maxTaskCreationDelay);
        view.setExport(export);
    }

    /// Shows the dialog for a filter, or a new one, owned by `opener` until it is closed.
    ///
    /// @param opener            What shows it.
    /// @param processorType     The type of processor the filter is for.
    /// @param pipelineRef       The pipeline the filter is for.
    /// @param filter            The filter, or `null` for a new one.
    /// @param defaultExpression A new filter's expression.
    /// @param consumer          Given the filter saved on OK, or `null` on Cancel.
    public void show(final HasControllerNode opener,
                     final ProcessorType processorType,
                     final DocRef pipelineRef,
                     final ProcessorFilter filter,
                     final ExpressionOperator defaultExpression,
                     final Consumer<ProcessorFilter> consumer) {
        final Long minMetaCreateTimeMs;
        final Long maxMetaCreateTimeMs;
        final Integer maxProcessingTasks;
        final String profileName;
        if (filter == null) {
            minMetaCreateTimeMs = null;
            maxMetaCreateTimeMs = null;
            maxProcessingTasks = null;
            profileName = null;
            runAsUser.setSelected(clientSecurityContext.getUserRef());
        } else {
            minMetaCreateTimeMs = filter.getMinMetaCreateTimeMs();
            maxMetaCreateTimeMs = filter.getMaxMetaCreateTimeMs();
            maxProcessingTasks = filter.getMaxProcessingTasks();
            profileName = filter.getProfileName();
            if (filter.getRunAsUser() == null) {
                runAsUser.setSelected(clientSecurityContext.getUserRef());
            } else {
                runAsUser.setSelected(filter.getRunAsUser());
            }
        }
        show(
                opener,
                processorType,
                pipelineRef,
                filter,
                defaultExpression,
                minMetaCreateTimeMs,
                maxMetaCreateTimeMs,
                maxProcessingTasks,
                profileName,
                consumer);
    }

    /// Shows the dialog for a filter, or a new one, with some of its values given, owned by `opener`
    /// until it is closed.
    ///
    /// @param opener              What shows it.
    /// @param processorType       The type of processor the filter is for.
    /// @param pipelineRef         The pipeline the filter is for.
    /// @param filter              The filter, or `null` for a new one.
    /// @param defaultExpression   A new filter's expression.
    /// @param minMetaCreateTimeMs The earliest create time of the streams processed, or `null`.
    /// @param maxMetaCreateTimeMs The latest create time of the streams processed, or `null`.
    /// @param maxProcessingTasks  The most tasks that may run at once, or `null`.
    /// @param profileName         The processing profile, or `null`.
    /// @param consumer            Given the filter saved on OK, or `null` on Cancel.
    public void show(final HasControllerNode opener,
                     final ProcessorType processorType,
                     final DocRef pipelineRef,
                     final ProcessorFilter filter,
                     final ExpressionOperator defaultExpression,
                     final Long minMetaCreateTimeMs,
                     final Long maxMetaCreateTimeMs,
                     final Integer maxProcessingTasks,
                     final String profileName,
                     final Consumer<ProcessorFilter> consumer) {
        prepare(processorType, pipelineRef, filter, defaultExpression, minMetaCreateTimeMs, maxMetaCreateTimeMs,
                maxProcessingTasks, profileName, consumer);

        // Show the processor creation dialog.
        final PopupSize popupSize = PopupSize.resizable(800, 800);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(existingFilter
                        ? "Edit Filter"
                        : "Add Filter")
                .onShow(e -> expressionEditor.focus())
                .onHideRequest(this::onHideRequest)
                .fire();
    }

    /// Shows the filter's values, ready for the dialog (package-private for its tests).
    ///
    /// @param processorType       The kind of processor the filter is for.
    /// @param pipelineRef         The pipeline or analytic rule that processes the streams.
    /// @param filter              The filter to edit, or `null` to add one.
    /// @param defaultExpression   The expression a new filter starts with, or `null` for an empty one.
    /// @param minMetaCreateTimeMs The earliest stream create time, or `null` for none.
    /// @param maxMetaCreateTimeMs The latest stream create time, or `null` for none.
    /// @param maxProcessingTasks  How many tasks may run at once, or `null` for no limit.
    /// @param profileName         The processing profile, or `null` for none.
    /// @param consumer            Given the filter saved, when OK saves it.
    void prepare(final ProcessorType processorType,
                 final DocRef pipelineRef,
                 final ProcessorFilter filter,
                 final ExpressionOperator defaultExpression,
                 final Long minMetaCreateTimeMs,
                 final Long maxMetaCreateTimeMs,
                 final Integer maxProcessingTasks,
                 final String profileName,
                 final Consumer<ProcessorFilter> consumer) {
        this.processorType = processorType;
        this.pipelineRef = pipelineRef;
        this.consumer = consumer;
        this.filter = filter;

        existingFilter = filter != null && filter.getId() != null;
        queryData = getOrCreateQueryData(filter, defaultExpression);
        fields = MetaFields.getProcessorFilterFields();
        final boolean export = NullSafe.getOrElse(filter, ProcessorFilter::isExport, false);
        final SimpleDuration maxTaskCreationDelay = NullSafe.get(filter, ProcessorFilter::getMaxTaskCreationDelay);
        feedDependencies = NullSafe.get(queryData, QueryData::getFeedDependencies);
        read(
                queryData.getExpression(),
                MetaFields.STREAM_STORE_DOC_REF,
                fields,
                minMetaCreateTimeMs,
                maxMetaCreateTimeMs,
                maxProcessingTasks,
                profileName,
                maxTaskCreationDelay,
                export);
    }

    /// OK saves the filter, if its expression is valid and the user is sure; Cancel gives nothing
    /// (package-private for its tests).
    ///
    /// @param e The dialog's request to hide, OK or Cancel.
    void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            final ExpressionOperator expression = expressionEditor.write();
            final Long minMetaCreateTime = view.getMinMetaCreateTimeMs();
            final Long maxMetaCreateTime = view.getMaxMetaCreateTimeMs();
            final Integer maxTasks = view.getMaxProcessingTasks();
            final String profile = view.getProfile();
            final boolean exportRead = view.isExport();

            validateExpression(fields, expression, e, () -> {
                try {
                    final QueryData qd = queryData
                            .copy()
                            .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                            .expression(expression)
                            .build();

                    if (existingFilter) {
                        ConfirmEvent.fire(
                                ProcessorEditController.this,
                                "You are about to update an existing filter. Any streams that " +
                                "might now be included by this filter but are older than the " +
                                "current tracker position will not be processed. " +
                                "Are you sure you wish to do this?",
                                result -> {
                                    if (result) {
                                        validateFeed(
                                                filter,
                                                qd,
                                                minMetaCreateTime,
                                                maxMetaCreateTime,
                                                maxTasks,
                                                profile,
                                                exportRead,
                                                e);
                                    } else {
                                        e.reset();
                                    }
                                });
                    } else {
                        validateFeed(null,
                                qd,
                                minMetaCreateTime,
                                maxMetaCreateTime,
                                maxTasks,
                                profile,
                                exportRead,
                                e);
                    }
                } catch (final RuntimeException ex) {
                    AlertEvent.fireError(ProcessorEditController.this, ex.getMessage(), e::reset);
                }
            });
        } else {
            consumer.accept(null);
            e.hide();
        }
    }

    private void onEditFeedDependencies() {
        feedDependenciesEditorProvider.get().edit(this, feedDependencies,
                updated -> this.feedDependencies = updated);
    }

    // Validates the expression, and if it isn't valid lets the user change it or cancel
    private void validateExpression(final List<QueryField> fields,
                                    final ExpressionOperator expression,
                                    final HidePopupRequestEvent event,
                                    final Runnable onSuccess) {

        restFactory
                .create(EXPRESSION_RESOURCE)
                .method(res -> res.validate(new ValidateExpressionRequest(
                        expression,
                        fields,
                        dateTimeSettingsFactory.getDateTimeSettings())))
                .onSuccess(result -> {
                    if (result.isOk()) {
                        onSuccess.run();
                    } else {
                        AlertEvent.fireError(ProcessorEditController.this, result.getString(), event::reset);
                    }
                })
                .onFailure(throwable ->
                        AlertEvent.fireError(ProcessorEditController.this, throwable.getMessage(), event::reset))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void hide(final ProcessorFilter result, final HidePopupRequestEvent event) {
        consumer.accept(result);
        event.hide();
    }

    private QueryData getOrCreateQueryData(final ProcessorFilter filter,
                                           final ExpressionOperator defaultExpression) {
        if (filter != null && filter.getQueryData() != null) {
            return filter.getQueryData();
        }
        return QueryData.builder().expression(defaultExpression).build();
    }

    private void validateFeed(final ProcessorFilter filter,
                              final QueryData queryData,
                              final Long minMetaCreateTimeMs,
                              final Long maxMetaCreateTimeMs,
                              final Integer maxProcessingTasks,
                              final String profileName,
                              final boolean export,
                              final HidePopupRequestEvent event) {

        final int feedCount = termCount(queryData, MetaFields.FEED);
        final int streamIdCount = termCount(queryData, MetaFields.ID);
        final int parentStreamIdCount = termCount(queryData, MetaFields.PARENT_ID);

        if (streamIdCount == 0
            && parentStreamIdCount == 0
            && feedCount == 0) {
            ConfirmEvent.fire(this,
                    "You are about to process all feeds. Are you sure you wish to do this?", result -> {
                        if (result) {
                            validateStreamType(
                                    filter,
                                    queryData,
                                    minMetaCreateTimeMs,
                                    maxMetaCreateTimeMs,
                                    maxProcessingTasks,
                                    profileName,
                                    export,
                                    event);
                        } else {
                            event.reset();
                        }
                    });
        } else {
            createOrUpdateProcessor(
                    filter,
                    queryData,
                    minMetaCreateTimeMs,
                    maxMetaCreateTimeMs,
                    maxProcessingTasks,
                    profileName,
                    export,
                    event);
        }
    }

    private void validateStreamType(final ProcessorFilter filter,
                                    final QueryData queryData,
                                    final Long minMetaCreateTimeMs,
                                    final Long maxMetaCreateTimeMs,
                                    final Integer maxProcessingTasks,
                                    final String profileName,
                                    final boolean export,
                                    final HidePopupRequestEvent event) {
        final int streamTypeCount = termCount(queryData, MetaFields.TYPE);
        final int streamIdCount = termCount(queryData, MetaFields.ID);
        final int parentStreamIdCount = termCount(queryData, MetaFields.PARENT_ID);

        if (streamIdCount == 0
            && parentStreamIdCount == 0
            && streamTypeCount == 0) {
            ConfirmEvent.fire(this,
                    "You are about to process all stream types. Are you sure you wish to do this?",
                    result -> {
                        if (result) {
                            createOrUpdateProcessor(
                                    filter,
                                    queryData,
                                    minMetaCreateTimeMs,
                                    maxMetaCreateTimeMs,
                                    maxProcessingTasks,
                                    profileName,
                                    export,
                                    event);
                        } else {
                            event.reset();
                        }
                    });
        } else {
            createOrUpdateProcessor(
                    filter,
                    queryData,
                    minMetaCreateTimeMs,
                    maxMetaCreateTimeMs,
                    maxProcessingTasks,
                    profileName,
                    export,
                    event);
        }
    }

    private int termCount(final QueryData queryData, final QueryField field) {
        if (queryData == null || queryData.getExpression() == null) {
            return 0;
        }
        return ExpressionUtil.termCount(queryData.getExpression(), field.getFldName());
    }

    private void createOrUpdateProcessor(final ProcessorFilter filter,
                                         final QueryData queryData,
                                         final Long minMetaCreateTimeMs,
                                         final Long maxMetaCreateTimeMs,
                                         final Integer maxProcessingTasks,
                                         final String profileName,
                                         final boolean export,
                                         final HidePopupRequestEvent event) {
        // Not threaded through the validation callbacks like the rest as the popup is still
        // showing while they run, so the view is still the source of truth.
        final SimpleDuration maxTaskCreationDelay = view.getMaxTaskCreationDelay();
        if (filter != null) {
            // Now update the processor filter using the find stream criteria.
            final ProcessorFilter updated = filter.copy()
                    .queryData(queryData.copy().feedDependencies(feedDependencies).build())
                    .minMetaCreateTimeMs(minMetaCreateTimeMs)
                    .maxMetaCreateTimeMs(maxMetaCreateTimeMs)
                    .maxProcessingTasks(maxProcessingTasks)
                    .profileName(profileName)
                    .maxTaskCreationDelay(maxTaskCreationDelay)
                    .export(export)
                    .runAsUser(runAsUser.getSelected())
                    .build();

            restFactory
                    .create(PROCESSOR_FILTER_RESOURCE)
                    .method(res -> res.update(updated.getId(), updated))
                    .onSuccess(r -> hide(r, event))
                    .onFailure(RestErrorHandler.forPopup(this, event))
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();

        } else {
            // Now create the processor filter using the find stream criteria.
            final CreateProcessFilterRequest request = CreateProcessFilterRequest
                    .builder()
                    .processorType(processorType)
                    .pipeline(pipelineRef)
                    .queryData(queryData)
                    .autoPriority(true)
                    .enabled(false)
                    .export(export)
                    .minMetaCreateTimeMs(minMetaCreateTimeMs)
                    .maxMetaCreateTimeMs(maxMetaCreateTimeMs)
                    .maxProcessingTasks(maxProcessingTasks)
                    .profileName(profileName)
                    .maxTaskCreationDelay(maxTaskCreationDelay)
                    .runAsUser(runAsUser.getSelected())
                    .build();
            restFactory
                    .create(PROCESSOR_FILTER_RESOURCE)
                    .method(res -> res.create(request))
                    .onSuccess(r -> hide(r, event))
                    // If it can't be created, let the user try again or cancel
                    .onFailure(RestErrorHandler.forPopup(this, event))
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }


}
