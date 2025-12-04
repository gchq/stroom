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

package stroom.pipeline.stepping.client.view;

import stroom.item.client.SelectionBox;
import stroom.pipeline.shared.XPathFilter.MatchType;
import stroom.pipeline.shared.XPathFilter.SearchType;
import stroom.pipeline.stepping.client.presenter.XPathFilterPresenter.XPathFilterView;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class XPathFilterViewImpl extends ViewImpl implements XPathFilterView {

    private final Widget widget;
    @UiField
    FormGroup valueContainer;
    @UiField
    FormGroup ignoreCaseContainer;
    @UiField
    TextBox xPath;
    @UiField
    FormGroup xPathContainer;
    @UiField
    SelectionBox<MatchType> matchType;
    @UiField
    SelectionBox<SearchType> searchType;
    @UiField
    TextBox value;
    @UiField
    CustomCheckBox ignoreCase;

    @Inject
    public XPathFilterViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        matchType.addItems(MatchType.values());
        matchType.setValue(MatchType.EQUALS);

        searchType.addItems(SearchType.values());
        searchType.setValue(SearchType.ALL);

        updateXPathVisibility();
    }

    private void updateXPathVisibility() {
        xPathContainer.setVisible(SearchType.XPATH.equals(searchType.getValue()));
    }

    @Override
    public void focus() {
        xPath.setFocus(true);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getXPath() {
        final SearchType selected = searchType.getValue();
        return switch (selected) {
            case XPATH -> xPath.getText();
            case ALL -> "//*";
            default -> "";
        };
    }

    @Override
    public void setXPath(final String xPath) {
        this.xPath.setText(xPath);
    }

    @Override
    public MatchType getMatchType() {
        return matchType.getValue();
    }

    @Override
    public void setMatchType(final MatchType matchType) {
        this.matchType.setValue(matchType);
        changeVisibility(matchType);
    }

    @Override
    public SearchType getSearchType() {
        return searchType.getValue();
    }

    @Override
    public void setSearchType(final SearchType searchType) {
        this.searchType.setValue(searchType);
        updateXPathVisibility();
    }

    @Override
    public String getValue() {
        return value.getText();
    }

    @Override
    public void setValue(final String value) {
        this.value.setText(value);
    }

    @Override
    public Boolean isIgnoreCase() {
        return ignoreCase.getValue();
    }

    @Override
    public void setIgnoreCase(final Boolean value) {
        ignoreCase.setValue(value);
    }

    private void changeVisibility(final MatchType matchType) {
        final boolean visible = matchType == MatchType.CONTAINS ||
                                matchType == MatchType.EQUALS ||
                                matchType == MatchType.NOT_EQUALS ||
                                matchType == MatchType.NOT_CONTAINS;
        valueContainer.setVisible(visible);
        ignoreCaseContainer.setVisible(visible);
    }

    @UiHandler("searchType")
    public void onSearchTypeChange(final ValueChangeEvent<SearchType> e) {
        final SearchType selected = e.getValue();
        switch (selected) {
            case ALL:
                xPathContainer.setVisible(false);
                break;
            case XPATH:
                xPathContainer.setVisible(true);
                xPath.setText("//");
                break;
        }
        updateXPathVisibility();
    }

    @UiHandler("matchType")
    public void onMatchTypeChange(final ValueChangeEvent<MatchType> e) {
        changeVisibility(e.getValue());
    }

    public interface Binder extends UiBinder<Widget, XPathFilterViewImpl> {

    }
}
