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

package stroom.activity.client;

import stroom.activity.client.ManageActivityPresenter.ManageActivityView;
import stroom.activity.shared.Activity;
import stroom.activity.shared.ActivityResource;
import stroom.alert.client.event.ConfirmEvent;
import stroom.core.client.UrlParameters;
import stroom.dispatch.client.RestFactory;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;

public class ManageActivityPresenter
        extends MyPresenterWidget<ManageActivityView>
        implements ManageActivityUiHandlers, HasHandlers {

    private static final ActivityResource ACTIVITY_RESOURCE = GWT.create(ActivityResource.class);
    public static final String LIST = "LIST";

    private final ActivityListPresenter listPresenter;
    private final Provider<ActivityEditPresenter> editProvider;
    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;
    private final UrlParameters urlParameters;
    private final CurrentActivity currentActivity;
    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;

    private final NameFilterTimer timer = new NameFilterTimer();
    private Supplier<SafeHtml> quickFilterTooltipSupplier;
    private boolean isFirstShow = true;

    @Inject
    public ManageActivityPresenter(final EventBus eventBus,
                                   final ManageActivityView view,
                                   final ActivityListPresenter listPresenter,
                                   final Provider<ActivityEditPresenter> editProvider,
                                   final RestFactory restFactory,
                                   final UiConfigCache uiConfigCache,
                                   final UrlParameters urlParameters,
                                   final CurrentActivity currentActivity) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.editProvider = editProvider;
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;
        this.urlParameters = urlParameters;
        this.currentActivity = currentActivity;

        getView().setUiHandlers(this);

        setInSlot(LIST, listPresenter);

        newButton = listPresenter.addButton(SvgPresets.NEW_ITEM);
        openButton = listPresenter.addButton(SvgPresets.EDIT);
        deleteButton = listPresenter.addButton(SvgPresets.DELETE);

        updateQuickFilterTooltipContentSupplier();
    }

    @Override
    protected void onBind() {
        getView().setTooltipContentSupplier(this::getQuickFilterTooltipSupplier);

        registerHandler(listPresenter.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                hide();
//                onOpen();
            }
        }));
        if (newButton != null) {
            registerHandler(newButton.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    onNew();
                }
            }));
        }
        if (openButton != null) {
            registerHandler(openButton.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    onOpen();
                }
            }));
        }
        if (deleteButton != null) {
            registerHandler(deleteButton.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    onDelete();
                }
            }));
        }

        super.onBind();
    }

    void showInitial(final Consumer<Activity> consumer) {
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                final boolean show = uiConfig.getActivity().isChooseOnStartup() &&
                        uiConfig.getActivity().isEnabled();
                if (show) {
                    if (urlParameters.isEmbedded()) {
                        // If we are in embedded more then see if we can find a current activity set in the session.
                        currentActivity.getActivity(activity -> {
                            // If no activity is set then ask the user to choose.
                            if (activity == null) {
                                show(null, consumer);
                            } else {
                                consumer.accept(activity);
                            }
                        }, this);
                    } else {
                        show(null, consumer);
                    }
                } else {
                    consumer.accept(null);
                }
            }
        }, this);
    }

    private void updateQuickFilterTooltipContentSupplier() {
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                final String helpUrl = uiConfig.getHelpUrlQuickFilter();
                restFactory
                        .create(ACTIVITY_RESOURCE)
                        .method(ActivityResource::listFieldDefinitions)
                        .onSuccess(fieldDefinitions -> {
                            quickFilterTooltipSupplier = () -> QuickFilterTooltipUtil.createTooltip(
                                    "Choose Activity Quick Filter",
                                    fieldDefinitions,
                                    helpUrl);
                        })
                        .onFailure(throwable -> {
                            // Just use the basic tooltip content
                            quickFilterTooltipSupplier = () -> QuickFilterTooltipUtil.createTooltip(
                                    "Choose Activity Quick Filter",
                                    helpUrl);
                        })
                        .taskMonitorFactory(this)
                        .exec();
            }
        }, this);
    }

    public void show(final Consumer<Activity> consumer) {
        currentActivity.getActivity(activity -> show(activity, consumer), this);
    }

    private void show(final Activity activity, final Consumer<Activity> consumer) {
        setSelected(activity);

        if (isFirstShow) {
            // Avoid the refresh on first show as this causes the data to be fetched twice
            isFirstShow = false;
        } else {
            listPresenter.refresh();
        }

        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                final String title = uiConfig.getActivity().getManagerTitle();
                final PopupSize popupSize = PopupSize.resizable(1000, 600);
                ShowPopupEvent.builder(this)
                        .popupType(PopupType.CLOSE_DIALOG)
                        .popupSize(popupSize)
                        .caption(title)
                        .modal(true)
                        .onShow(e -> getView().focus())
                        .onHide(e -> {
                            final Activity selected = getSelected();
                            currentActivity.setActivity(selected, this);
                            consumer.accept(getSelected());
                        })
                        .fire();

                enableButtons();
            }
        }, this);
    }

    public void hide() {
        HidePopupRequestEvent.builder(this).fire();
    }

    private void enableButtons() {
        final Activity activity = getSelected();
        final boolean enabled = activity != null &&
                activity.getDetails() != null &&
                activity.getDetails().getProperties() != null &&
                activity.getDetails().getProperties().size() > 0;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
        if (enabled) {
            EnablePopupEvent.builder(this).fire();
        } else {
            DisablePopupEvent.builder(this).fire();
        }
    }

    private void onOpen() {
        final Activity e = getSelected();
        if (e != null) {
            // Load the activity.
            restFactory
                    .create(ACTIVITY_RESOURCE)
                    .method(res -> res.fetch(e.getId()))
                    .onSuccess(this::onEdit)
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    private void onEdit(final Activity e) {
        if (e != null) {
            if (editProvider != null) {
                final ActivityEditPresenter editor = editProvider.get();
                editor.show(e, activity -> {
                    listPresenter.refresh();
                    setSelected(activity);
                    updateQuickFilterTooltipContentSupplier();
                });
            }
        }
    }

    private void onDelete() {
        final Activity entity = getSelected();
        if (entity != null) {
            ConfirmEvent.fire(this, "Are you sure you want to delete the selected " +
                            getEntityDisplayType() + "?",
                    result -> {
                        if (result) {
                            // Delete the activity
                            restFactory
                                    .create(ACTIVITY_RESOURCE)
                                    .method(res -> res.delete(entity.getId()))
                                    .onSuccess(success -> {
                                        listPresenter.refresh();
                                        listPresenter.getSelectionModel().clear();
                                        updateQuickFilterTooltipContentSupplier();
                                    })
                                    .taskMonitorFactory(this)
                                    .exec();
                        }
                    });
        }
    }

    private String getEntityDisplayType() {
        return "activity";
    }

    @Override
    public void changeNameFilter(final String name) {
        timer.setName(name);
        timer.cancel();
        timer.schedule(350);
    }

    private void onNew() {
        onEdit(Activity.create());
    }

    private void setSelected(final Activity activity) {
        listPresenter.getSelectionModel().clear();
        if (activity != null) {
            listPresenter.getSelectionModel().setSelected(activity);
        }
    }

    private Activity getSelected() {
        return listPresenter.getSelectionModel().getSelected();
    }

    public SafeHtml getQuickFilterTooltipSupplier() {
        return quickFilterTooltipSupplier.get();
    }

    public interface ManageActivityView extends View, Focus, HasUiHandlers<ManageActivityUiHandlers> {

        void setTooltipContentSupplier(final Supplier<SafeHtml> tooltipSupplier);
    }

    private class NameFilterTimer extends Timer {

        private String name;

        @Override
        public void run() {
            String filter = name;
            if (filter != null) {
                filter = filter.trim();
                if (filter.length() == 0) {
                    filter = null;
                }
            }

            if (!Objects.equals(filter, listPresenter.getCriteria())) {

                listPresenter.setCriteria(name);
                listPresenter.refresh();
            }
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
