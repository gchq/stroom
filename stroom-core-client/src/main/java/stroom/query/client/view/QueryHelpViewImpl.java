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

package stroom.query.client.view;

import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.query.client.presenter.QueryHelpPresenter.QueryHelpView;
import stroom.query.client.presenter.QueryHelpUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class QueryHelpViewImpl extends ViewWithUiHandlers<QueryHelpUiHandlers> implements QueryHelpView {

    private final Widget widget;

    @UiField
    QuickFilter nameFilter;
    @UiField
    SimplePanel elementChooser;
    @UiField
    SimplePanel details;
    @UiField
    InlineSvgButton copyButton;
    @UiField
    InlineSvgButton insertButton;

    @Inject
    public QueryHelpViewImpl(final Binder binder,
                             final UiConfigCache uiConfigCache) {
        widget = binder.createAndBindUi(this);

        copyButton.setSvg(SvgImage.COPY);
        insertButton.setSvg(SvgImage.INSERT);

        uiConfigCache.get()
                .onSuccess(uiConfig ->
                        nameFilter.registerPopupTextProvider(() ->
                                QuickFilterTooltipUtil.createTooltip(
                                        "Query Item Quick Filter",
                                        ExplorerTreeFilter.FIELD_DEFINITIONS,
                                        uiConfig.getHelpUrl())));
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setElementChooser(final Widget view) {
        elementChooser.setWidget(view);
    }

    @Override
    public void setDetails(final SafeHtml details) {
        final String htmlStr = details != null
                ? details.asString()
                : "";
        this.details.getElement().setInnerHTML(htmlStr);
    }

    @Override
    public void enableButtons(final boolean enable) {
        copyButton.setEnabled(enable);
        insertButton.setEnabled(enable);
    }

    @UiHandler("nameFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().changeQuickFilter(nameFilter.getText());
    }

    @UiHandler("copyButton")
    public void onCopyClick(final ClickEvent event) {
        getUiHandlers().onCopy();
    }

    @UiHandler("insertButton")
    public void onInsertClick(final ClickEvent event) {
        getUiHandlers().onInsert();
    }

    public interface Binder extends UiBinder<Widget, QueryHelpViewImpl> {

    }
}
