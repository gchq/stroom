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

package stroom.annotation.client;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.CellTable.Resources;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.gwt.view.client.SingleSelectionModel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.table.client.CellTableView;
import stroom.data.table.client.CellTableViewImpl;
import stroom.data.table.client.CellTableViewImpl.BasicResources;
import stroom.data.table.client.CellTableViewImpl.HoverResources;

import java.util.List;

public class StatusPresenter extends MyPresenterWidget<CellTableView<String>> {
    private final SingleSelectionModel<String> selectionModel = new SingleSelectionModel<>();

    @Inject
    public StatusPresenter(final EventBus eventBus) {
        super(eventBus, new CellTableViewImpl<>(true, (Resources) GWT.create(HoverResources.class)));
//        this.eventBus = eventBus;

//        // Checked.
//        final Column<DocumentType, TickBoxState> checkedColumn = new Column<DocumentType, TickBoxState>(
//                TickBoxCell.create(false, true)) {
//            @Override
//            public TickBoxState getValue(final DocumentType documentType) {
//                return TickBoxState.fromBoolean(selected.contains(documentType.getType()));
//            }
//        };
//        checkedColumn.setFieldUpdater((index, object, value) -> {
//            if (selected.contains(object.getType())) {
//                selected.remove(object.getType());
//            } else {
//                selected.add(object.getType());
//            }
//
//            DataSelectionEvent.fire(StatusPresenter.this, StatusPresenter.this, false);
//        });
//        getView().addColumn(checkedColumn);
//
//        // Icon.
//        final Column<String, SafeHtml> iconColumn = new Column<DocumentType, SafeHtml>(new SafeHtmlCell()) {
//            @Override
//            public SafeHtml getValue(final String status) {
//                return SafeHtmlUtils.fromTrustedString("<img style=\"width:16px;height:16px;padding:2px\" src=\"" + ImageUtil.getImageURL() + object.getIconUrl() + "\"/>");
//            }
//        };
//        getView().addColumn(iconColumn);
//
//        // Text.
//        final Column<DocumentType, String> textColumn = new Column<DocumentType, String>(new TextCell()) {
//            @Override
//            public String getValue(final DocumentType documentType) {
//                return documentType.getDisplayType();
//            }
//        };

        // Text.
        final Column<String, SafeHtml> textColumn = new Column<String, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final String string) {
                final SafeHtmlBuilder builder = new SafeHtmlBuilder();
                builder.appendHtmlConstant("<div style=\"padding: 5px; min-width: 200px\">");
                builder.appendEscaped(string);
                builder.appendHtmlConstant("</div>");
                return builder.toSafeHtml();
            }
        };
        getView().addColumn(textColumn);
        getView().setSupportsSelection(true);
        getView().setSelectionModel(selectionModel);


//        final Style style = getView().asWidget().getElement().getStyle();
//        style.setPaddingLeft(1, Unit.PX);
//        style.setPaddingRight(3, Unit.PX);
//        style.setPaddingTop(2, Unit.PX);
//        style.setPaddingBottom(1, Unit.PX);
    }

    void setStatusValues(final List<String> statusValues) {
        getView().setRowData(0, statusValues);
        getView().setRowCount(statusValues.size());
    }

    String getSelected() {
        return selectionModel.getSelectedObject();
    }

    void setSelected(final String status) {
//        selectionModel.clear();
        selectionModel.setSelected(status, true);
    }

    HandlerRegistration addDataSelectionHandler(final SelectionChangeEvent.Handler handler) {
        return selectionModel.addSelectionChangeHandler(handler);
    }
}
