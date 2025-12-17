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

package stroom.query.client.view;

import stroom.item.client.SelectionList;
import stroom.query.client.presenter.QueryHelpPresenter.QueryHelpView;
import stroom.query.client.presenter.QueryHelpSelectionItem;
import stroom.query.client.presenter.QueryHelpUiHandlers;
import stroom.query.shared.QueryHelpRow;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.function.Supplier;

public class QueryHelpViewImpl extends ViewWithUiHandlers<QueryHelpUiHandlers> implements QueryHelpView {

    private final Widget widget;

    @UiField
    SelectionList<QueryHelpRow, QueryHelpSelectionItem> selectionList;
    @UiField
    SimplePanel details;
    @UiField
    InlineSvgButton copyButton;
    @UiField
    InlineSvgButton insertButton;

    @Inject
    public QueryHelpViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        copyButton.setSvg(SvgImage.COPY);
        insertButton.setSvg(SvgImage.INSERT);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public SelectionList<QueryHelpRow, QueryHelpSelectionItem> getSelectionList() {
        return selectionList;
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

    @Override
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        selectionList.registerPopupTextProvider(popupTextSupplier);
    }

    @UiHandler("copyButton")
    public void onCopyClick(final ClickEvent event) {
        getUiHandlers().onCopy();
    }

    @UiHandler("insertButton")
    public void onInsertClick(final ClickEvent event) {
        getUiHandlers().onInsert();
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, QueryHelpViewImpl> {

    }
}
