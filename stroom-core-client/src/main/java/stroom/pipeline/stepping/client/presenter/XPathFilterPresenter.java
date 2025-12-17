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

package stroom.pipeline.stepping.client.presenter;

import stroom.pipeline.shared.XPathFilter;
import stroom.pipeline.shared.XPathFilter.MatchType;
import stroom.pipeline.shared.XPathFilter.SearchType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class XPathFilterPresenter extends MyPresenterWidget<XPathFilterPresenter.XPathFilterView> {

    final PopupSize popupSize = PopupSize.resizableX(700);
    private XPathFilter xPathFilter;

    @Inject
    public XPathFilterPresenter(final EventBus eventBus, final XPathFilterView view) {
        super(eventBus, view);
    }

    public void write() {
        xPathFilter.setPath(getView().getXPath());
        xPathFilter.setSearchType(getView().getSearchType());
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
        getView().setXPath(xPathFilter.getPath());
        getView().setSearchType(xPathFilter.getSearchType());
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

    public void add(final XPathFilter xPathFilter, final HidePopupRequestEvent.Handler handler) {
        this.xPathFilter = xPathFilter;
        read(xPathFilter);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Add XPath Filter")
                .onShow(e -> getView().focus())
                .onHideRequest(handler)
                .fire();
    }

    public void edit(final XPathFilter xPathFilter, final HidePopupRequestEvent.Handler handler) {
        this.xPathFilter = xPathFilter;
        read(xPathFilter);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Edit XPath Filter")
                .onShow(e -> getView().focus())
                .onHideRequest(handler)
                .fire();
    }


    // --------------------------------------------------------------------------------


    public interface XPathFilterView extends View, Focus {

        SearchType getSearchType();

        void setSearchType(SearchType searchType);

        String getXPath();

        void setXPath(String xPath);

        MatchType getMatchType();

        void setMatchType(MatchType matchType);

        String getValue();

        void setValue(String value);

        Boolean isIgnoreCase();

        void setIgnoreCase(Boolean ignoreCase);
    }
}
