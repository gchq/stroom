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

package stroom.activity.client;

import stroom.activity.client.ManageActivityPresenter.ManageActivityView;
import stroom.activity.shared.Activity;
import stroom.activity.shared.ActivityResource;
import stroom.alert.client.event.ConfirmEvent;
import stroom.core.client.UrlParameters;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.filter.FilterFieldDefinition;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Timer;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import javax.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ManageActivityPresenter extends
        MyPresenterWidget<ManageActivityView> implements ManageActivityUiHandlers, HasHandlers {
    private static final ActivityResource ACTIVITY_RESOURCE = GWT.create(ActivityResource.class);
    public static final String LIST = "LIST";

    private final ActivityListPresenter listPresenter;
    private final Provider<ActivityEditPresenter> editProvider;
    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;
    private final UrlParameters urlParameters;
    private final CurrentActivity currentActivity;
    private ButtonView newButton;
    private ButtonView openButton;
    private ButtonView deleteButton;

    private final NameFilterTimer timer = new NameFilterTimer();
    private Supplier<SafeHtml> quickFilterTooltipSupplier;

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

        registerHandler(listPresenter.getView().getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                hide();
//                onOpen();
            }
        }));
        if (newButton != null) {
            registerHandler(newButton.addClickHandler(event -> {
                if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
                    onNew();
                }
            }));
        }
        if (openButton != null) {
            registerHandler(openButton.addClickHandler(event -> {
                if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
                    onOpen();
                }
            }));
        }
        if (deleteButton != null) {
            registerHandler(deleteButton.addClickHandler(event -> {
                if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
                    onDelete();
                }
            }));
        }

        super.onBind();
    }

    void showInitial(final Consumer<Activity> consumer) {
        uiConfigCache.get().onSuccess(uiConfig -> {
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
                    });
                } else {
                    show(null, consumer);
                }
            } else {
                consumer.accept(null);
            }
        });
    }

    private void updateQuickFilterTooltipContentSupplier() {
        final Rest<List<FilterFieldDefinition>> rest = restFactory.create();
        // Separate to aid type inference
        rest
                .onSuccess(fieldDefinitions -> {
                    quickFilterTooltipSupplier = () -> QuickFilterTooltipUtil.createTooltip(
                            "Choose Activity Quick Filter",
                            fieldDefinitions);
                })
                .onFailure(throwable -> {
                    // Just use the basic tooltip content
                    quickFilterTooltipSupplier = () -> QuickFilterTooltipUtil.createTooltip(
                            "Choose Activity Quick Filter");
                })
                .call(ACTIVITY_RESOURCE)
                .listFieldDefinitions();
    }

    public void show(final Consumer<Activity> consumer) {
        currentActivity.getActivity(activity -> show(activity, consumer));
    }

    private void show(final Activity activity, final Consumer<Activity> consumer) {
        setSelected(activity);
        listPresenter.refresh();

        final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                hide();
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                final Activity activity = getSelected();
                currentActivity.setActivity(activity);
                consumer.accept(getSelected());
            }
        };
        uiConfigCache.get().onSuccess(uiConfig -> {
            final String title = uiConfig.getActivity().getManagerTitle();
            final PopupSize popupSize = new PopupSize(1000, 600, true);
            ShowPopupEvent.fire(ManageActivityPresenter.this, ManageActivityPresenter.this,
                    PopupType.CLOSE_DIALOG, null, popupSize, title, popupUiHandlers, null);
            enableButtons();
        });
    }

    public void hide() {
        HidePopupEvent.fire(ManageActivityPresenter.this, ManageActivityPresenter.this);
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
            EnablePopupEvent.fire(this, this);
        } else {
            DisablePopupEvent.fire(this, this);
        }
    }

    private void onOpen() {
        final Activity e = getSelected();
        if (e != null) {
            // Load the activity.
            final Rest<Activity> rest = restFactory.create();
            rest
                    .onSuccess(this::onEdit)
                    .call(ACTIVITY_RESOURCE)
                    .read(e.getId());
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
            ConfirmEvent.fire(this, "Are you sure you want to delete the selected " + getEntityDisplayType() + "?",
                    result -> {
                        if (result) {
                            // Delete the activity
                            final Rest<Activity> rest = restFactory.create();
                            rest
                                    .onSuccess(success -> {
                                        listPresenter.refresh();
                                        listPresenter.getView().getSelectionModel().clear();
                                        updateQuickFilterTooltipContentSupplier();
                                    })
                                    .call(ACTIVITY_RESOURCE)
                                    .delete(entity.getId());
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

    interface ManageActivityView extends View, HasUiHandlers<ManageActivityUiHandlers> {

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
