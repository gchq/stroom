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

package stroom.explorer.client.presenter;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.CellTable.Resources;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.table.client.CellTableView;
import stroom.data.table.client.CellTableViewImpl;
import stroom.data.table.client.CellTableViewImpl.BasicResources;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.util.client.ImageUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TypeFilterPresenter extends MyPresenterWidget<CellTableView<DocumentType>>
        implements HasDataSelectionHandlers<TypeFilterPresenter> {
    private final EventBus eventBus;

    private final Set<String> selected = new HashSet<>();
    private List<DocumentType> visibleTypes;

    private final String SELECT_ALL_OR_NONE_TEXT = "All/none";
    private final String SELECT_ALL_OR_NONE_ICON = "document/SelectAllOrNone.svg";
    private final DocumentType SELECT_ALL_OR_NONE_DOCUMENT_TYPE = new DocumentType(
            1, SELECT_ALL_OR_NONE_TEXT, SELECT_ALL_OR_NONE_TEXT, SELECT_ALL_OR_NONE_ICON);

    @Inject
    public TypeFilterPresenter(final EventBus eventBus) {
        super(eventBus, new CellTableViewImpl<>(false, (Resources) GWT.create(BasicResources.class)));
        this.eventBus = eventBus;

        // Checked.
        final Column<DocumentType, TickBoxState> checkedColumn = new Column<DocumentType, TickBoxState>(
                TickBoxCell.create(false, true)) {
            @Override
            public TickBoxState getValue(final DocumentType object) {
                // If we're checking the TickBoxState of 'All/none' then we need some logic for half-ticks.
                if(object.equals(SELECT_ALL_OR_NONE_DOCUMENT_TYPE)) {
                    if (selected.size()  == 0){
                        return TickBoxState.UNTICK;
                    }
                    else if (selected.size()  < visibleTypes.size()) {
                        return TickBoxState.HALF_TICK;
                    }
                    else {
                        return TickBoxState.TICK;
                    }
                }
                else {
                    return TickBoxState.fromBoolean(selected.contains(object.getType()));
                }
            }
        };
        checkedColumn.setFieldUpdater((index, object, value) -> {
            if(object.equals(SELECT_ALL_OR_NONE_DOCUMENT_TYPE)){
                if(value.toBoolean()){
                    showAll();
                }
                else {
                    hideAll();
                }
            } else {
                if (selected.contains(object.getType())) {
                    selected.remove(object.getType());
                } else {
                    selected.add(object.getType());
                }
            }
            // We need to refresh the view here otherwise a selection change wouldn't mean a change
            // to the TickBoxState of the 'All/none' TickBox.
            refreshView();
            DataSelectionEvent.fire(TypeFilterPresenter.this, TypeFilterPresenter.this, false);
        });
        getView().addColumn(checkedColumn);

        // Icon.
        final Column<DocumentType, SafeHtml> iconColumn = new Column<DocumentType, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final DocumentType object) {
                return SafeHtmlUtils.fromTrustedString("<img style=\"width:16px;height:16px;padding:2px\" src=\"" + ImageUtil.getImageURL() + object.getIconUrl() + "\"/>");
            }
        };
        getView().addColumn(iconColumn);

        // Text.
        final Column<DocumentType, SafeHtml> textColumn = new Column<DocumentType, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final DocumentType object) {
                // We want to make the 'All/none' entry bold, so we'll use some SafeHtml.
                if(object.getType().equalsIgnoreCase(SELECT_ALL_OR_NONE_TEXT)){
                    return SafeHtmlUtils.fromTrustedString( "<strong>" + object.getType() + "</strong>");
                }
                else {
                    return SafeHtmlUtils.fromTrustedString(object.getType());
                }
            }
        };
        getView().addColumn(textColumn);

        final Style style = getView().asWidget().getElement().getStyle();
        style.setPaddingLeft(1, Unit.PX);
        style.setPaddingRight(3, Unit.PX);
        style.setPaddingTop(2, Unit.PX);
        style.setPaddingBottom(1, Unit.PX);
    }

    public void setDocumentTypes(final DocumentTypes documentTypes) {
        visibleTypes = documentTypes.getVisibleTypes();
        showAll();
        refreshView();
    }

    private void showAll(){
        for (final DocumentType documentType : visibleTypes) {
            selected.add(documentType.getType());
        }
    }

    private void hideAll() {
        selected.clear();
    }

    private void refreshView() {
        // We want to add in the 'All/none' DocumentType, at the top.
        List<DocumentType> selectableTypes = new ArrayList<>(visibleTypes);
        selectableTypes.add(0, SELECT_ALL_OR_NONE_DOCUMENT_TYPE);

        // To refresh the view we need to set the row data again.
        getView().setRowData(0, selectableTypes);
        getView().setRowCount(selectableTypes.size());
    }

    public Set<String> getIncludedTypes() {
        return selected;
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<TypeFilterPresenter> handler) {
        return eventBus.addHandlerToSource(DataSelectionEvent.getType(), this, handler);
    }
}