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

package stroom.importexport.client.presenter;

import stroom.cell.info.client.ActionCell;
import stroom.cell.info.client.CommandLink;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.DocumentPluginEventManager;
import stroom.document.client.event.DeleteDocumentEvent;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerResource;
import stroom.importexport.client.event.ShowDependenciesInfoDialogEvent;
import stroom.importexport.client.event.ShowDocRefDependenciesEvent;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuBuilder;
import stroom.widget.menu.client.presenter.MenuPresenter;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DependenciesPresenter extends MyPresenterWidget<PagerView> {

    private static final ContentResource CONTENT_RESOURCE = GWT.create(ContentResource.class);
    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    public static final int DEFAULT_PAGE_SIZE = 100;

    private static final int COL_WIDTH_TYPE = 120;
    private static final int COL_WIDTH_NAME = 300;

    private final RestFactory restFactory;
    private final DependencyCriteria criteria;
    private final RestDataProvider<Dependency, ResultPage<Dependency>> dataProvider;
    private final MyDataGrid<Dependency> dataGrid;
    private final DocumentPluginEventManager entityPluginEventManager;
    private final MenuPresenter menuPresenter;

    // Holds all the doc type icons
    private Map<String, SvgImage> typeToSvgMap = new HashMap<>();

    @Inject
    public DependenciesPresenter(final EventBus eventBus,
                                 final PagerView view,
                                 final RestFactory restFactory,
                                 final DocumentPluginEventManager entityPluginEventManager,
                                 final MenuPresenter menuPresenter) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(100);
        view.setDataWidget(dataGrid);

        this.restFactory = restFactory;
        criteria = new DependencyCriteria();

        refreshDocTypeIcons();

        dataProvider = new RestDataProvider<Dependency, ResultPage<Dependency>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<Dependency>> dataConsumer,
                                final Consumer<Throwable> throwableConsumer) {
                CriteriaUtil.setRange(criteria, range);
                final Rest<ResultPage<Dependency>> rest = restFactory.create();
                rest
                        .onSuccess(dataConsumer)
                        .onFailure(throwableConsumer)
                        .call(CONTENT_RESOURCE)
                        .fetchDependencies(criteria);
            }
        };
        dataProvider.addDataDisplay(dataGrid);
        this.entityPluginEventManager = entityPluginEventManager;
        this.menuPresenter = menuPresenter;
        initColumns();
    }

    private void initColumns() {

        // From (Icon)
        dataGrid.addColumn(DataGridUtil.svgPresetColumnBuilder(false, (Dependency row) ->
                                getDocTypeIcon(row.getFrom()))
                        .build(),
                "<br/>",
                ColumnSizeConstants.ICON_COL);

        // From (Type)
        dataGrid.addResizableColumn(DataGridUtil.textColumnBuilder((Dependency row) ->
                                getValue(row, Dependency::getFrom, DocRef::getType))
                        .withSorting(DependencyCriteria.FIELD_FROM_TYPE, true)
                        .build(),
                DependencyCriteria.FIELD_FROM_TYPE,
                COL_WIDTH_TYPE);

        // From (Name)
        final Column<Dependency, CommandLink> fromNameColumn = DataGridUtil.commandLinkColumnBuilder((Dependency row) ->
                        getName(row, Dependency::getFrom, true))
                .withSorting(DependencyCriteria.FIELD_FROM_NAME, true)
                .build();
        DataGridUtil.addCommandLinkFieldUpdater(fromNameColumn);
        dataGrid.addResizableColumn(fromNameColumn,
                DependencyCriteria.FIELD_FROM_NAME,
                COL_WIDTH_NAME);

        // From (action menu))
        addActionButtonColumn(Dependency::getFrom, "", 40);

        // To (Icon)
        dataGrid.addColumn(DataGridUtil.svgPresetColumnBuilder(false, (Dependency row) ->
                                getDocTypeIcon(row.getTo()))
                        .build(),
                "<br/>",
                ColumnSizeConstants.ICON_COL);

        // To (Type)
        dataGrid.addResizableColumn(DataGridUtil.textColumnBuilder((Dependency row) ->
                                getValue(row, Dependency::getTo, DocRef::getType))
                        .withSorting(DependencyCriteria.FIELD_TO_TYPE, true)
                        .build(),
                DependencyCriteria.FIELD_TO_TYPE,
                COL_WIDTH_TYPE);

        // To (Name)
        final Column<Dependency, CommandLink> toNameColumn = DataGridUtil.commandLinkColumnBuilder((Dependency row) ->
                        getName(row, Dependency::getTo, false))
                .withSorting(DependencyCriteria.FIELD_TO_NAME, true)
                .build();
        DataGridUtil.addCommandLinkFieldUpdater(toNameColumn);
        dataGrid.addResizableColumn(toNameColumn,
                DependencyCriteria.FIELD_TO_NAME,
                COL_WIDTH_NAME);

        // To (action menu)
        addActionButtonColumn(Dependency::getTo, "", 40);

        // Status
        dataGrid.addResizableColumn(DataGridUtil.htmlColumnBuilder(this::getStatusValue)
                        .withSorting(DependencyCriteria.FIELD_STATUS, false)
                        .centerAligned()
                        .build(),
                DataGridUtil.createCenterAlignedHeader(DependencyCriteria.FIELD_STATUS),
                60);

        DataGridUtil.addEndColumn(dataGrid);
        DataGridUtil.addColumnSortHandler(dataGrid, criteria, dataProvider::refresh);
    }

    private void addActionButtonColumn(final Function<Dependency, DocRef> docRefSelector,
                                       final String name,
                                       final int width) {
        final ActionCell<DocRef> actionCell = new ActionCell<>(this::showActionMenu);
        final Column<Dependency, DocRef> actionColumn = DataGridUtil.columnBuilder(
                docRefSelector,
                Function.identity(),
                () -> actionCell
        ).build();
        dataGrid.addColumn(actionColumn, name, width);
    }

    private void showActionMenu(final DocRef docRef, final NativeEvent event) {

        final PopupPosition popupPosition = new PopupPosition(event.getClientX() + 10, event.getClientY());
        menuPresenter.setData(buildActionMenu(docRef));
        ShowPopupEvent.builder(menuPresenter)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .fire();
    }

    private List<Item> buildActionMenu(final DocRef docRef) {

        return MenuBuilder.builder()
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(SvgImage.INFO)
                        .text("Properties")
                        .command(() -> onDocInfo(docRef)))
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(SvgImage.DELETE)
                        .text("Delete")
                        .command(() -> onDeleteDoc(docRef)))
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(SvgImage.SHOW)
                        .text("Reveal in Explorer")
                        .command(() -> onRevealDoc(docRef)))
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(SvgImage.DEPENDENCIES)
                        .text("Show dependencies")
                        .command(() -> onShowDependencies(docRef)))
                .build();
    }

    /**
     * Open a document
     */
    private void onOpenDoc(final DocRef docRef) {
        OpenDocumentEvent.fire(DependenciesPresenter.this, docRef, true);
    }

    /**
     * Reveal the doc in the Explorer tree
     */
    private void onRevealDoc(final DocRef docRef) {
        entityPluginEventManager.highlight(docRef);
    }

    private void onDocInfo(final DocRef docRef) {
        ShowDependenciesInfoDialogEvent.fire(DependenciesPresenter.this, docRef);
    }

    private void onDeleteDoc(final DocRef docRef) {
        DeleteDocumentEvent.fire(
                DependenciesPresenter.this,
                Collections.singletonList(docRef),
                true,
                result -> refresh());
    }

    private void onShowDependencies(final DocRef docRef) {
        ShowDocRefDependenciesEvent.fire(DependenciesPresenter.this, docRef);
    }

    private void refreshDocTypeIcons() {

        // Hold map of doc type icons keyed on type to save constructing for each row
        final Rest<DocumentTypes> rest = restFactory.create();
        rest
                .onSuccess(documentTypes -> {
                    typeToSvgMap = documentTypes.getVisibleTypes().stream()
                            .collect(Collectors.toMap(
                                    DocumentType::getType,
                                    DocumentType::getIcon));

                    // Special case for Searchable as it is not a normal doc type
                    // Not ideal defining it here but adding it fetchDocumentTypes causes problems
                    // with the explorer context menus.
                    typeToSvgMap.putIfAbsent(
                            "Searchable",
                            SvgImage.DOCUMENT_SEARCHABLE);
                })
                .call(EXPLORER_RESOURCE)
                .fetchDocumentTypes();
    }

    private CommandLink getName(final Dependency row,
                                final Function<Dependency, DocRef> docRefExtractor,
                                final boolean from) {
        final DocRef docRef = docRefExtractor.apply(row);
        if (docRef != null) {
            if (from | row.isOk()) {
                return new CommandLink(docRef.getName(), () -> onOpenDoc(docRef));
            } else {
                return new CommandLink(docRef.getName(), null);
            }
        } else {
            return null;
        }
    }

    private String getValue(final Dependency row,
                            final Function<Dependency, DocRef> docRefExtractor,
                            final Function<DocRef, String> valueExtractor) {
        final DocRef docRef = docRefExtractor.apply(row);
        if (docRef != null) {
            return valueExtractor.apply(docRef);
        } else {
            return null;
        }
    }

    private Preset getDocTypeIcon(final DocRef docRef) {
        if (docRef != null && docRef.getType() != null && !docRef.getType().isEmpty()) {
            final SvgImage svgPreset = typeToSvgMap.get(docRef.getType());
            if (svgPreset != null) {
                return new Preset(svgPreset, docRef.getType(), true);
            } else {
                return SvgPresets.ALERT.title("Unknown Document Type");
            }
        } else {
            return SvgPresets.ALERT.title("Unknown Document Type");
        }
    }

    private SafeHtml getStatusValue(final Dependency row) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        final String value;
        final String commonStyles = "font-weight:bold";
        if (row.isOk()) {
            value = "OK";
            builder.appendHtmlConstant("<span style=\"color:green;" + commonStyles + "\">");
        } else {
            value = "Missing";
            builder.appendHtmlConstant("<span style=\"color:red;" + commonStyles + "\">");
        }
        builder.appendEscaped(value);
        builder.appendHtmlConstant("</span>");
        return builder.toSafeHtml();
    }

    void setFilterInput(final String filterInput) {
        this.criteria.setPartialName(filterInput);
        // Changing the filter means any existing offset is wrong, so we need to reset to the initial state
        resetRange();
    }

    void clearFilterInput() {
        this.criteria.setPartialName(null);
        // Changing the filter means any existing offset is wrong, so we need to reset to the initial state
        resetRange();
    }

    private void resetRange() {
        dataGrid.setVisibleRange(new Range(0, DEFAULT_PAGE_SIZE));
    }

    void refresh() {
        this.dataProvider.refresh();
    }
}
