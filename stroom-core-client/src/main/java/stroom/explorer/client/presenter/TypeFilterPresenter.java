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
import com.google.gwt.cell.client.TextCell;
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
import stroom.cell.info.client.SvgCell;
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
import stroom.svg.client.SvgPreset;
import stroom.util.client.ImageUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TypeFilterPresenter extends MyPresenterWidget<CellTableView<DocumentType>>
        implements HasDataSelectionHandlers<TypeFilterPresenter> {
    private final Set<String> selected = new HashSet<String>();
    private final EventBus eventBus;

    @Inject
    public TypeFilterPresenter(final EventBus eventBus) {
        super(eventBus, new CellTableViewImpl<DocumentType>(false, (Resources) GWT.create(BasicResources.class)));
        this.eventBus = eventBus;

        // Checked.
        final Column<DocumentType, TickBoxState> checkedColumn = new Column<DocumentType, TickBoxState>(
                TickBoxCell.create(false, true)) {
            @Override
            public TickBoxState getValue(final DocumentType object) {
                return TickBoxState.fromBoolean(selected.contains(object.getType()));
            }
        };
        checkedColumn.setFieldUpdater((index, object, value) -> {
            if (selected.contains(object.getType())) {
                selected.remove(object.getType());
            } else {
                selected.add(object.getType());
            }

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
        final Column<DocumentType, String> textColumn = new Column<DocumentType, String>(new TextCell()) {
            @Override
            public String getValue(final DocumentType object) {
                return object.getType();
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
        final List<DocumentType> visibleTypes = documentTypes.getVisibleTypes();

        selected.clear();
        for (final DocumentType documentType : visibleTypes) {
            selected.add(documentType.getType());
        }

        getView().setRowData(0, visibleTypes);
        getView().setRowCount(visibleTypes.size());
    }

    public Set<String> getIncludedTypes() {
        return selected;
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<TypeFilterPresenter> handler) {
        return eventBus.addHandlerToSource(DataSelectionEvent.getType(), this, handler);
    }
}
