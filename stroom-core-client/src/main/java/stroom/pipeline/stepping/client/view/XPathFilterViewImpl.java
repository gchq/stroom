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

package stroom.pipeline.stepping.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.item.client.ItemListBox;
import stroom.pipeline.shared.XPathFilter.MatchType;
import stroom.pipeline.stepping.client.presenter.XPathFilterPresenter.XPathFilterView;
import stroom.widget.tickbox.client.view.TickBox;

public class XPathFilterViewImpl extends ViewImpl implements XPathFilterView {
    public interface Binder extends UiBinder<Widget, XPathFilterViewImpl> {
    }

    private final Widget widget;
    @UiField
    Grid grid;
    @UiField
    TextBox xPath;
    @UiField
    ItemListBox<MatchType> matchType;
    @UiField
    TextBox value;
    @UiField
    TickBox ignoreCase;

    @Inject
    public XPathFilterViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        matchType.addItems(MatchType.values());
        matchType.setSelectedItem(MatchType.EQUALS);

        matchType.addSelectionHandler(event -> changeVisibility(event.getSelectedItem()));
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getXPath() {
        return xPath.getText();
    }

    @Override
    public void setXPath(final String xPath) {
        this.xPath.setText(xPath);
    }

    @Override
    public MatchType getMatchType() {
        return matchType.getSelectedItem();
    }

    @Override
    public void setMatchType(final MatchType matchType) {
        this.matchType.setSelectedItem(matchType);
        changeVisibility(matchType);
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
        return ignoreCase.getValue().toBoolean();
    }

    @Override
    public void setIgnoreCase(final Boolean value) {
        ignoreCase.setBooleanValue(value);
    }

    private void changeVisibility(final MatchType matchType) {
        final boolean visible = matchType != null && (matchType == MatchType.CONTAINS || matchType == MatchType.EQUALS);
        grid.getRowFormatter().setVisible(2, visible);
        grid.getRowFormatter().setVisible(3, visible);
    }
}
