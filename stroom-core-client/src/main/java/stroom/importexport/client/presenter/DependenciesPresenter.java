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

package stroom.importexport.client.presenter;

import stroom.cell.info.client.ActionCell;
import stroom.cell.info.client.CommandLink;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.document.client.event.DeleteDocumentEvent;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.explorer.client.event.LocateDocEvent;
import stroom.explorer.shared.ExplorerResource;
import stroom.importexport.client.event.ShowDependenciesInfoDialogEvent;
import stroom.importexport.client.event.ShowDocRefDependenciesEvent;
import stroom.importexport.client.event.ShowDocRefDependenciesEvent.DependencyType;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DependenciesPresenter
        extends MyPresenterWidget<PagerView> {

    private static final ContentResource CONTENT_RESOURCE = GWT.create(ContentResource.class);
    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private static final int COL_WIDTH_TYPE = 120;
    private static final int COL_WIDTH_NAME = 300;

    private final RestFactory restFactory;
    private final DependencyCriteria criteria;
    private final RestDataProvider<Dependency, ResultPage<Dependency>> dataProvider;
    private final MyDataGrid<Dependency> dataGrid;
    private final MenuPresenter menuPresenter;

    private Set<String> openableTypes = new HashSet<>();

    @Inject
    public DependenciesPresenter(final EventBus eventBus,
                                 final PagerView view,
                                 final RestFactory restFactory,
                                 final MenuPresenter menuPresenter) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this, 100);
        view.setDataWidget(dataGrid);

        this.restFactory = restFactory;
        criteria = new DependencyCriteria();

        refreshDocTypeIcons();

        dataProvider = new RestDataProvider<Dependency, ResultPage<Dependency>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<Dependency>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(criteria, range);
                CriteriaUtil.setSortList(criteria, dataGrid.getColumnSortList());
                restFactory
                        .create(CONTENT_RESOURCE)
                        .method(res -> res.fetchDependencies(criteria))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
                        .taskMonitorFactory(view)
                        .exec();
            }
        };
        dataProvider.addDataDisplay(dataGrid);
        this.menuPresenter = menuPresenter;
        initColumns();
    }

    @Override
    protected void onBind() {
        registerHandler(dataGrid.addColumnSortHandler(event -> dataProvider.refresh()));
    }

    private void initColumns() {

        // From (Icon)
        dataGrid.addColumn(DataGridUtil.svgPresetColumnBuilder(false, (Dependency row) ->
                                getDocTypeIcon(row.getFrom()))
                        .build(),
                "<br/>",
                ColumnSizeConstants.ICON_COL);

        // From (Type)
        dataGrid.addAutoResizableColumn(DataGridUtil.textColumnBuilder((Dependency row) ->
                                getValue(row, Dependency::getFrom, DocRef::getType))
                        .withSorting(DependencyCriteria.FIELD_FROM_TYPE, true)
                        .build(),
                DependencyCriteria.FIELD_FROM_TYPE,
                COL_WIDTH_TYPE);

        // From (Name)
        final Column<Dependency, CommandLink> fromNameColumn = DataGridUtil.commandLinkColumnBuilder(
                        (Dependency row) ->
                                getName(row, Dependency::getFrom, true))
                .withSorting(DependencyCriteria.FIELD_FROM_NAME, true)
                .build();
        DataGridUtil.addCommandLinkFieldUpdater(fromNameColumn);
        dataGrid.addAutoResizableColumn(fromNameColumn,
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
        dataGrid.addAutoResizableColumn(DataGridUtil.textColumnBuilder((Dependency row) ->
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
        dataGrid.addAutoResizableColumn(toNameColumn,
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
    }

    private void addActionButtonColumn(final Function<Dependency, DocRef> docRefSelector,
                                       final String name,
                                       final int width) {
        final ActionCell<DocRef> actionCell = new ActionCell<>(this::showActionMenu);
        final Column<Dependency, DocRef> actionColumn = DataGridUtil.columnBuilder(
                docRefSelector,
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
                        .icon(SvgImage.LOCATE)
                        .text("Locate in Explorer")
                        .command(() -> onLocateDoc(docRef)))
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(SvgImage.DEPENDENCIES)
                        .text("Show dependencies")
                        .command(() -> onShowDependencies(docRef, DependencyType.DEPENDENCY)))
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(SvgImage.DEPENDENCIES)
                        .text("Show dependants")
                        .command(() -> onShowDependencies(docRef, DependencyType.DEPENDANT)))
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
    private void onLocateDoc(final DocRef docRef) {
        LocateDocEvent.fire(this, docRef);
    }

    private void onDocInfo(final DocRef docRef) {
        ShowDependenciesInfoDialogEvent.fire(DependenciesPresenter.this, docRef);
    }

    private void onDeleteDoc(final DocRef docRef) {
        DeleteDocumentEvent.fire(
                DependenciesPresenter.this,
                Collections.singletonList(docRef),
                true,
                result -> refresh(),
                this);
    }

    private void onShowDependencies(final DocRef docRef, final DependencyType dependencyType) {
        ShowDocRefDependenciesEvent.fire(DependenciesPresenter.this, docRef, dependencyType);
    }

    private void refreshDocTypeIcons() {

        // Hold map of doc type icons keyed on type to save constructing for each row
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(ExplorerResource::fetchDocumentTypes)
                .onSuccess(documentTypes -> {
                    openableTypes = documentTypes
                            .getTypes()
                            .stream()
                            .map(DocumentType::getType)
                            .collect(Collectors.toSet());
                })
                .taskMonitorFactory(this)
                .exec();
    }

    private CommandLink getName(final Dependency row,
                                final Function<Dependency, DocRef> docRefExtractor,
                                final boolean from) {
        final DocRef docRef = docRefExtractor.apply(row);
        if (docRef != null) {
            if (from || (openableTypes.contains(docRef.getType()) && row.isOk())) {
                final String name = NullSafe.requireNonNullElseGet(
                        docRef.getName(),
                        docRef::getUuid);
                return new CommandLink(
                        docRef.getName(),
                        "Open " + docRef.getType() + " '" + name + "'",
                        () -> onOpenDoc(docRef));
            } else {
                return CommandLink.withoutCommand(docRef.getName());
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
            final SvgImage icon = DocumentTypeRegistry.getIcon(docRef.getType());
            if (icon != null) {
                return new Preset(icon, docRef.getType(), true);
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
            builder.appendHtmlConstant("<span style=\"color:var(--icon-colour__green);" + commonStyles + "\">");
        } else {
            value = "Missing";
            builder.appendHtmlConstant("<span style=\"color:var(--icon-colour__red);" + commonStyles + "\">");
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
        dataGrid.setVisibleRange(new Range(0, PageRequest.DEFAULT_PAGE_LENGTH));
    }

    void refresh() {
        this.dataProvider.refresh();
    }
}
