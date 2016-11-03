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
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import stroom.pipeline.shared.XPathFilter;
import stroom.pipeline.shared.XPathFilter.MatchType;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class XPathFilterPresenter extends MyPresenterWidget<XPathFilterPresenter.XPathFilterView> {
    public interface XPathFilterView extends View {
        String getXPath();

        void setXPath(String xPath);

        MatchType getMatchType();

        void setMatchType(MatchType matchType);

        String getValue();

        void setValue(String value);

        Boolean isIgnoreCase();

        void setIgnoreCase(Boolean ignoreCase);
    }

    final PopupSize popupSize = new PopupSize(400, 150, 400, 150, 1000, 150, true);

    private XPathFilter xPathFilter;

    @Inject
    public XPathFilterPresenter(final EventBus eventBus, final XPathFilterView view) {
        super(eventBus, view);
    }

    public void write() {
        xPathFilter.setXPath(getView().getXPath());
        xPathFilter.setMatchType(getView().getMatchType());

        if (getView().getMatchType().isNeedsValue()) {
            xPathFilter.setValue(getView().getValue());
            xPathFilter.setIgnoreCase(getView().isIgnoreCase());
        } else {
            xPathFilter.setValue(null);
            xPathFilter.setIgnoreCase(null);
        }
    }

    public void read(final XPathFilter xPathFilter) {
        getView().setXPath(xPathFilter.getXPath());
        getView().setMatchType(xPathFilter.getMatchType());
        if (xPathFilter.getValue() == null) {
            getView().setValue("");
        } else {
            getView().setValue(xPathFilter.getValue());
        }
        if (xPathFilter.isIgnoreCase() == null) {
            getView().setIgnoreCase(true);
        } else {
            getView().setIgnoreCase(xPathFilter.isIgnoreCase());
        }
    }

    public void add(final XPathFilter xPathFilter, final PopupUiHandlers popupUiHandlers) {
        this.xPathFilter = xPathFilter;
        read(xPathFilter);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Add XPath Filter", popupUiHandlers);
    }

    public void edit(final XPathFilter xPathFilter, final PopupUiHandlers popupUiHandlers) {
        this.xPathFilter = xPathFilter;
        read(xPathFilter);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Edit XPath Filter", popupUiHandlers);
    }
}
