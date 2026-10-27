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

package stroom.activity.client.component;

import stroom.activity.client.CurrentActivity;
import stroom.activity.shared.Activity;
import stroom.activity.shared.ActivityResource;
import stroom.alert.client.event.ConfirmEvent;
import stroom.core.client.UrlParameters;
import stroom.dispatch.client.RestFactory;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.svg.shared.SvgImage;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.button.client.ButtonView;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// The activity chooser ([ManageActivity]), in a dialog made for one use: the user's activities,
/// to choose the current one from, add to, edit and delete. Closing it makes the chosen activity
/// the current one.
public class ManageActivityController extends Controller {

    private static final ActivityResource ACTIVITY_RESOURCE = GWT.create(ActivityResource.class);

    private final ManageActivity view;
    private final ActivityListController activityList;
    private final Provider<ActivityEditController> editProvider;
    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;
    private final UrlParameters urlParameters;
    private final CurrentActivity currentActivity;
    private final ButtonView openButton;
    private final ButtonView deleteButton;

    private final NameFilterTimer timer = new NameFilterTimer();
    private Supplier<SafeHtml> quickFilterTooltipSupplier = () -> null;

    /// Made by GIN, with its content and list.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param view                             The content.
    /// @param activityList                     The user's activities.
    /// @param editProvider                     Makes the dialog that edits an activity.
    /// @param restFactory                      Fetches and deletes activities.
    /// @param uiConfigCache                    Says whether activities are enabled, and the dialog's
    ///                                         caption.
    /// @param urlParameters                    Says whether Stroom is embedded.
    /// @param currentActivity                  The current activity, set when the dialog closes.
    /// @param quickFilterContextHandlerFactory Makes the filter's history and fields.
    @Inject
    public ManageActivityController(final EventBus eventBus,
                                    final ManageActivity view,
                                    final ActivityListController activityList,
                                    final Provider<ActivityEditController> editProvider,
                                    final RestFactory restFactory,
                                    final UiConfigCache uiConfigCache,
                                    final UrlParameters urlParameters,
                                    final CurrentActivity currentActivity,
                                    final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        this.view = view;
        this.activityList = own(activityList);
        this.editProvider = editProvider;
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;
        this.urlParameters = urlParameters;
        this.currentActivity = currentActivity;
        initWidget(view);

        view.setList(activityList);
        // A rejected filter comes back as an empty page like any other, so surface the reason on
        // the filter box. See ResultPage.filterError.
        activityList.setFilterErrorConsumer(view::setFilterError);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                ActivityResource.QUICK_FILTER_CONTEXT, this, () -> taskMonitor().createTaskMonitor()));
        view.setOnFilterChange(this::changeNameFilter);
        view.setTooltipContentSupplier(() -> quickFilterTooltipSupplier.get());

        activityList.getToolbar().setName("Activity actions");
        activityList.getToolbar().button(SvgImage.ADD, "New Activity", this::onNew);
        openButton = activityList.getToolbar().button(SvgImage.EDIT, "Edit Selected Activity", this::onOpen);
        openButton.setEnabled(false);
        deleteButton = activityList.getToolbar().button(SvgImage.DELETE, "Delete Selected Activity", this::onDelete);
        deleteButton.setEnabled(false);

        updateQuickFilterTooltipContentSupplier();

        own(activityList.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                ControllerDialog.pressAction(this, DialogAction.CLOSE);
            }
        }));
        onDispose(timer::cancel);
    }

    /// Shows the chooser at startup, if the UI config says activities are chosen then. When Stroom
    /// is embedded, the current activity is used instead if there is one. Not shown, it disposes
    /// itself.
    ///
    /// @param opener   What shows it.
    /// @param consumer Given the chosen activity once the chooser closes, or the current one, or
    ///                 `null` if the chooser isn't shown.
    public void showInitial(final HasControllerNode opener, final Consumer<Activity> consumer) {
        uiConfigCache.get(guard(uiConfig -> {
            if (uiConfig == null) {
                dispose();
            } else if (uiConfig.getActivity().isChooseOnStartup() && uiConfig.getActivity().isEnabled()) {
                if (urlParameters.isEmbedded()) {
                    // If we are in embedded mode then see if we can find a current activity set in
                    // the session.
                    currentActivity.getActivity(activity -> {
                        // If no activity is set then ask the user to choose.
                        if (activity == null) {
                            show(opener, null, consumer);
                        } else {
                            dispose();
                            consumer.accept(activity);
                        }
                    }, taskMonitor());
                } else {
                    show(opener, null, consumer);
                }
            } else {
                dispose();
                consumer.accept(null);
            }
        }), taskMonitor());
    }

    /// Shows the chooser with the current activity chosen.
    ///
    /// @param opener   What shows it.
    /// @param consumer Given the chosen activity once the chooser closes.
    public void show(final HasControllerNode opener, final Consumer<Activity> consumer) {
        currentActivity.getActivity(guard(activity -> show(opener, activity, consumer)), taskMonitor());
    }

    private void show(final HasControllerNode opener,
                      final Activity activity,
                      final Consumer<Activity> consumer) {
        setSelected(activity);

        uiConfigCache.get(guard(uiConfig -> {
            if (uiConfig == null) {
                dispose();
            } else {
                ControllerDialog.showOnce(opener, this)
                        .popupType(PopupType.CLOSE_DIALOG)
                        .popupSize(PopupSize.resizable(1000, 600))
                        .caption(uiConfig.getActivity().getManagerTitle())
                        .modal(true)
                        .onShow(e -> view.focus())
                        .onHide(e -> {
                            final Activity selected = getSelected();
                            currentActivity.setActivity(selected, taskMonitor());
                            consumer.accept(selected);
                        })
                        .fire();

                enableButtons();
            }
        }), taskMonitor());
    }

    private void updateQuickFilterTooltipContentSupplier() {
        uiConfigCache.get(guard(uiConfig -> {
            if (uiConfig != null) {
                final String helpUrl = uiConfig.getHelpUrlQuickFilter();
                restFactory
                        .create(ACTIVITY_RESOURCE)
                        .method(ActivityResource::listFieldDefinitions)
                        .onSuccess(fieldDefinitions ->
                                quickFilterTooltipSupplier = () -> QuickFilterTooltipUtil.createTooltip(
                                        "Choose Activity Quick Filter",
                                        fieldDefinitions,
                                        helpUrl))
                        .onFailure(error ->
                                // Just use the basic tooltip content
                                quickFilterTooltipSupplier = () -> QuickFilterTooltipUtil.createTooltip(
                                        "Choose Activity Quick Filter",
                                        helpUrl))
                        .owner(this)
                        .taskMonitorFactory(taskMonitor())
                        .exec();
            }
        }), taskMonitor());
    }

    private void enableButtons() {
        final Activity activity = getSelected();
        final boolean enabled = activity != null
                                && activity.getDetails() != null
                                && activity.getDetails().getProperties() != null
                                && !activity.getDetails().getProperties().isEmpty();
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
        ControllerDialog.setActionEnabled(this, DialogAction.CLOSE, enabled);
    }

    private void onOpen() {
        final Activity selected = getSelected();
        if (selected != null) {
            // Load the activity.
            restFactory
                    .create(ACTIVITY_RESOURCE)
                    .method(res -> res.fetch(selected.getId()))
                    .onSuccess(this::onEdit)
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

    private void onEdit(final Activity activity) {
        if (activity != null) {
            uiConfigCache.get(guard(uiConfig -> {
                if (uiConfig != null && uiConfig.getActivity().isEnabled()) {
                    editProvider.get().edit(this, activity, uiConfig.getActivity(), edited -> {
                        activityList.refresh();
                        setSelected(edited);
                        updateQuickFilterTooltipContentSupplier();
                    });
                }
            }), taskMonitor());
        }
    }

    private void onDelete() {
        final Activity selected = getSelected();
        if (selected != null) {
            ConfirmEvent.fire(this, "Are you sure you want to delete the selected activity?", result -> {
                if (result) {
                    restFactory
                            .create(ACTIVITY_RESOURCE)
                            .method(res -> res.delete(selected.getId()))
                            .onSuccess(success -> {
                                activityList.refresh();
                                activityList.getSelectionModel().clear();
                                updateQuickFilterTooltipContentSupplier();
                            })
                            .owner(this)
                            .taskMonitorFactory(taskMonitor())
                            .exec();
                }
            });
        }
    }

    private void changeNameFilter(final String name) {
        timer.setName(name);
        timer.cancel();
        timer.schedule(350);
    }

    private void onNew() {
        onEdit(Activity.builder().build());
    }

    private void setSelected(final Activity activity) {
        activityList.getSelectionModel().clear();
        if (activity != null) {
            activityList.getSelectionModel().setSelected(activity);
        }
    }

    private Activity getSelected() {
        return activityList.getSelectionModel().getSelected();
    }

    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }


    // --------------------------------------------------------------------------------


    private class NameFilterTimer extends Timer {

        private String name;

        @Override
        public void run() {
            String filter = name;
            if (filter != null) {
                filter = filter.trim();
                if (filter.isEmpty()) {
                    filter = null;
                }
            }

            if (!Objects.equals(filter, activityList.getCriteria())) {
                activityList.setCriteria(name);
                activityList.refresh();
            }
        }

        void setName(final String name) {
            this.name = name;
        }
    }
}
