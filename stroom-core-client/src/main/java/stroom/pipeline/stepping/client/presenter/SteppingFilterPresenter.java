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

package stroom.pipeline.stepping.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import stroom.pipeline.shared.SteppingFilterSettings;
import stroom.pipeline.shared.XPathFilter;
import stroom.pipeline.stepping.client.event.ShowSteppingFilterSettingsEvent;
import stroom.pipeline.stepping.client.event.ShowSteppingFilterSettingsEvent.ShowSteppingFilterSettingsHandler;
import stroom.util.shared.OutputState;
import stroom.util.shared.Severity;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.HashSet;
import java.util.List;

public class SteppingFilterPresenter extends
        MyPresenter<SteppingFilterPresenter.SteppingFilterSettingsView, SteppingFilterPresenter.SteppingFilterSettingsProxy>
        implements SteppingFilterUiHandlers, ShowSteppingFilterSettingsHandler, PopupUiHandlers {
    public interface SteppingFilterSettingsView extends View, HasUiHandlers<SteppingFilterUiHandlers> {
        Severity getSkipToErrors();

        void setSkipToErrors(Severity severity);

        OutputState getSkipToOutput();

        void setSkipToOutput(OutputState skipToOutput);

        void setEditEnabled(boolean enabled);

        void setRemoveEnabled(boolean enabled);
    }

    @ProxyCodeSplit
    public interface SteppingFilterSettingsProxy extends Proxy<SteppingFilterPresenter> {
    }

    public static final String LIST = "LIST";

    private ShowSteppingFilterSettingsEvent event;
    private SteppingFilterSettings settings;
    private final XPathListPresenter xPathListPresenter;
    private List<XPathFilter> xPathFilters;

    private final XPathFilterPresenter xPathFilterPresenter;

    @Inject
    public SteppingFilterPresenter(final EventBus eventBus, final SteppingFilterSettingsView view,
                                   final SteppingFilterSettingsProxy proxy, final XPathListPresenter xPathListPresenter,
                                   final XPathFilterPresenter xPathFilterProvider) {
        super(eventBus, view, proxy);
        this.xPathListPresenter = xPathListPresenter;
        this.xPathFilterPresenter = xPathFilterProvider;
        view.setUiHandlers(this);

        setInSlot(LIST, xPathListPresenter);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(xPathListPresenter.getSelectionModel().addSelectionHandler(event -> {
            final List<XPathFilter> list = xPathListPresenter.getSelectionModel().getSelectedItems();
            getView().setEditEnabled(list.size() == 1);
            getView().setRemoveEnabled(list.size() > 0);

            if (event.getSelectionType().isDoubleSelect()) {
                if (list.size() == 1) {
                    editXPathFilter();
                }
            }
        }));
    }

    @Override
    public void addXPathFilter() {
        final XPathFilter xPathFilter = new XPathFilter();
        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    xPathFilterPresenter.write();
                    xPathFilters.add(xPathFilter);

                    xPathListPresenter.getSelectionModel().setSelected(xPathFilter);
                }
                HidePopupEvent.fire(SteppingFilterPresenter.this, xPathFilterPresenter);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        };
        xPathFilterPresenter.add(xPathFilter, popupUiHandlers);
    }

    @Override
    public void editXPathFilter() {
        final List<XPathFilter> list = xPathListPresenter.getSelectionModel().getSelectedItems();
        if (list.size() == 1) {
            final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    if (ok) {
                        xPathFilterPresenter.write();
                        xPathListPresenter.refresh();
                    }
                    HidePopupEvent.fire(SteppingFilterPresenter.this, xPathFilterPresenter);
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                    // Do nothing.
                }
            };
            xPathFilterPresenter.edit(list.get(0), popupUiHandlers);
        }
    }

    @Override
    public void removeXPathFilter() {
        // If there is a selected filter remove it from the set of filters and
        // the display.
        final List<XPathFilter> list = xPathListPresenter.getSelectionModel().getSelectedItems();
        if (list.size() > 0) {
            xPathFilters.removeAll(list);
            xPathListPresenter.refresh();
            xPathListPresenter.getSelectionModel().clear();
        }
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            write();
        }

        HidePopupEvent.fire(this, this);
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
        // Do nothing.
    }

    @ProxyEvent
    @Override
    public void onShow(final ShowSteppingFilterSettingsEvent event) {
        this.event = event;
        this.settings = event.getSettings();
        read();
        revealInParent();
    }

    @Override
    protected void revealInParent() {
        String caption;
        if (event.isInput()) {
            caption = "Change '" + event.getElementId() + "' Input Filter";
        } else {
            caption = "Change '" + event.getElementId() + "' Output Filter";
        }

        final PopupSize popupSize = new PopupSize(650, 400, 650, 400, 1000, 1000, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, caption, this);
    }

    private void read() {
        getView().setSkipToErrors(settings.getSkipToSeverity());
        getView().setSkipToOutput(settings.getSkipToOutput());

        xPathFilters = xPathListPresenter.getDataProvider().getList();
        xPathFilters.clear();
        if (settings.getXPathFilters() != null && settings.getXPathFilters().size() > 0) {
            xPathFilters.addAll(settings.getXPathFilters());
        }
    }

    private void write() {
        settings.setSkipToSeverity(getView().getSkipToErrors());
        settings.setSkipToOutput(getView().getSkipToOutput());
        settings.setXPathFilters(null);
        settings.setXPathFilters(new HashSet<>(xPathFilters));

        event.getEditor().setFilterActive(settings.isActive());
    }
}
