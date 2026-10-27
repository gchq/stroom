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

package stroom.pipeline.stepping.client.component;

import stroom.item.client.SelectionBox;
import stroom.pipeline.shared.XPathFilter.MatchType;
import stroom.pipeline.shared.XPathFilter.SearchType;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.view.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;

/// The XPath filter dialog's content, for stepping: where to look in the output, and what
/// to match there.
public class XPathFilterEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

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
    TickBox ignoreCase;

    /// Makes the content.
    public XPathFilterEditor() {
        initWidget(BINDER.createAndBindUi(this));
        matchType.addItems(MatchType.values());
        matchType.setValue(MatchType.EQUALS);

        searchType.addItems(SearchType.values());
        searchType.setValue(SearchType.ALL);

        updateXPathVisibility();
    }

    private void updateXPathVisibility() {
        xPathContainer.setVisible(SearchType.XPATH.equals(searchType.getValue()));
    }

    /// Puts the focus in the XPath.
    public void focus() {
        xPath.setFocus(true);
    }

    /// @return The XPath to look at: the one entered, or every element for All text.
    public String getXPath() {
        final SearchType selected = searchType.getValue();
        return switch (selected) {
            case XPATH -> xPath.getText();
            case ALL -> "//*";
            default -> "";
        };
    }

    /// @param xPath The XPath.
    public void setXPath(final String xPath) {
        this.xPath.setText(xPath);
    }

    /// @return How the value is matched.
    public MatchType getMatchType() {
        return matchType.getValue();
    }

    /// @param matchType How the value is matched.
    public void setMatchType(final MatchType matchType) {
        this.matchType.setValue(matchType);
        changeVisibility(matchType);
    }

    /// @return Where to look.
    public SearchType getSearchType() {
        return searchType.getValue();
    }

    /// @param searchType Where to look.
    public void setSearchType(final SearchType searchType) {
        this.searchType.setValue(searchType);
        updateXPathVisibility();
    }

    /// @return The value to match.
    public String getValue() {
        return value.getText();
    }

    /// @param value The value to match.
    public void setValue(final String value) {
        this.value.setText(value);
    }

    /// @return Whether case is ignored.
    public Boolean isIgnoreCase() {
        return ignoreCase.getValue();
    }

    /// @param value Whether case is ignored.
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
    void onSearchTypeChange(final ValueChangeEvent<SearchType> e) {
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
    void onMatchTypeChange(final ValueChangeEvent<MatchType> e) {
        changeVisibility(e.getValue());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, XPathFilterEditor> {

    }
}
