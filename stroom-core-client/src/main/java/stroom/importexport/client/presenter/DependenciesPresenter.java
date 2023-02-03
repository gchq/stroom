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
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.DocumentPluginEventManager;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerResource;
import stroom.importexport.client.event.ShowDependenciesInfoDialogEvent;
import stroom.importexport.client.event.ShowDocRefDependenciesEvent;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.receive.rules.client.presenter.ActionMenuPresenter;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.util.client.Console;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuBuilder;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Provider;

public class DependenciesPresenter extends MyPresenterWidget<DataGridView<Dependency>> {

    private static final ContentResource CONTENT_RESOURCE = GWT.create(ContentResource.class);
    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    public static final int DEFAULT_PAGE_SIZE = 100;

    private static final int COL_WIDTH_TYPE = 120;
    private static final int COL_WIDTH_NAME = 300;

    private static final Preset SEARCHABLE_PRESET = new Preset(
            DocumentType.DOC_IMAGE_CLASS_NAME + "searchable.svg",
            "Searchable",
            true);
    private static final Preset DOC_INFO_PRESET = SvgPresets.INFO.title("Properties");
    private static final Preset REVEAL_DOC_PRESET = SvgPresets.SHOW.title("Reveal in Explorer");
    private static final Preset SHOW_DEPENDENCIES_PRESET = SvgPresets.DEPENDENCIES.title("Show dependencies");

    private final RestFactory restFactory;
    private final DependencyCriteria criteria;
    private final RestDataProvider<Dependency, ResultPage<Dependency>> dataProvider;
    private final DocumentPluginEventManager entityPluginEventManager;
    private final Provider<ActionMenuPresenter> actionMenuPresenterProvider;

    // Holds all the doc type icons
    private Map<String, Preset> typeToSvgMap = new HashMap<>();

    @Inject
    public DependenciesPresenter(final EventBus eventBus,
                                 final RestFactory restFactory,
                                 final DocumentPluginEventManager entityPluginEventManager,
                                 final Provider<ActionMenuPresenter> actionMenuPresenterProvider) {
        super(eventBus, new DataGridViewImpl<>(false, DEFAULT_PAGE_SIZE));

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
        dataProvider.addDataDisplay(getView().getDataDisplay());
        this.entityPluginEventManager = entityPluginEventManager;
        this.actionMenuPresenterProvider = actionMenuPresenterProvider;

        initColumns();
    }

    private void initColumns() {

        // From (Icon)
        getView().addColumn(DataGridUtil.svgPresetColumnBuilder(false, (Dependency row) ->
                                getDocTypeIcon(row.getFrom()))
                        .build(),
                "<br/>",
                ColumnSizeConstants.ICON_COL);

        // From (Type)
        getView().addResizableColumn(DataGridUtil.textColumnBuilder((Dependency row) ->
                                getValue(row, Dependency::getFrom, DocRef::getType))
                        .withSorting(DependencyCriteria.FIELD_FROM_TYPE, true)
                        .build(),
                DependencyCriteria.FIELD_FROM_TYPE,
                COL_WIDTH_TYPE);

        // From (Name)
        final Column<Dependency, String> fromNameColumn = DataGridUtil.hyperlinkColumnBuilder((Dependency row) ->
                        getValue(row, Dependency::getFrom, DocRef::getName))
                .withSorting(DependencyCriteria.FIELD_FROM_NAME, true)
                .build();
        fromNameColumn.setFieldUpdater((index, object, value) -> onOpenDoc(object.getFrom()));
        getView().addResizableColumn(fromNameColumn,
                DependencyCriteria.FIELD_FROM_NAME,
                COL_WIDTH_NAME);

        // From (action menu))
        addActionButtonColumn(Dependency::getFrom, "", 40);

        // To (Icon)
        getView().addColumn(DataGridUtil.svgPresetColumnBuilder(false, (Dependency row) ->
                                getDocTypeIcon(row.getTo()))
                        .build(),
                "<br/>",
                ColumnSizeConstants.ICON_COL);

        // To (Type)
        getView().addResizableColumn(DataGridUtil.textColumnBuilder((Dependency row) ->
                                getValue(row, Dependency::getTo, DocRef::getType))
                        .withSorting(DependencyCriteria.FIELD_TO_TYPE, true)
                        .build(),
                DependencyCriteria.FIELD_TO_TYPE,
                COL_WIDTH_TYPE);

        // To (Name)
        final Column<Dependency, String> toNameColumn = DataGridUtil.hyperlinkColumnBuilder((Dependency row) ->
                        getValue(row, Dependency::getTo, DocRef::getName))
                .withSorting(DependencyCriteria.FIELD_TO_NAME, true)
                .build();
        toNameColumn.setFieldUpdater((index, object, value) -> onOpenDoc(object.getTo()));
        getView().addResizableColumn(toNameColumn,
                DependencyCriteria.FIELD_TO_NAME,
                COL_WIDTH_NAME);

        // To (action menu)
        addActionButtonColumn(Dependency::getTo, "", 40);

        // Status
        getView().addResizableColumn(DataGridUtil.htmlColumnBuilder(this::getStatusValue)
                        .withSorting(DependencyCriteria.FIELD_STATUS, false)
                        .centerAligned()
                        .build(),
                DataGridUtil.createCenterAlignedHeader(DependencyCriteria.FIELD_STATUS),
                60);

        DataGridUtil.addEndColumn(getView());
        DataGridUtil.addColumnSortHandler(getView(), criteria, dataProvider::refresh);
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
        getView().addColumn(actionColumn, name, width);
    }

    private void showActionMenu(final DocRef docRef, final NativeEvent event) {

        List<Item> items = buildActionMenu(docRef);
        actionMenuPresenterProvider.get().show(
                DependenciesPresenter.this,
                items,
                event.getClientX(),
                event.getClientY());
    }

    private List<Item> buildActionMenu(final DocRef docRef) {

        return MenuBuilder.builder()
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .withIcon(DOC_INFO_PRESET)
                        .withText(DOC_INFO_PRESET.getTitle())
                        .withCommand(() -> onDocInfo(docRef)))
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .withIcon(REVEAL_DOC_PRESET)
                        .withText(REVEAL_DOC_PRESET.getTitle())
                        .withCommand(() -> onRevealDoc(docRef)))
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .withIcon(SHOW_DEPENDENCIES_PRESET)
                        .withText(SHOW_DEPENDENCIES_PRESET.getTitle())
                        .withCommand(() -> onShowDependencies(docRef)))
                .build();
    }

    /**
     * Open a document
     */
    private void onOpenDoc(final DocRef docRef) {
        Console.log("Opening doc: " + docRef);
        entityPluginEventManager.open(docRef, true);
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
                                    documentType ->
                                            new Preset(
                                                    documentType.getIconClassName(),
                                                    documentType.getDisplayType(),
                                                    true)));

                    // Special case for Searchable as it is not a normal doc type
                    // Not ideal defining it here but adding it fetchDocumentTypes causes problems
                    // with the explorer context menus.
                    typeToSvgMap.putIfAbsent(
                            "Searchable",
                            SEARCHABLE_PRESET);
                })
                .call(EXPLORER_RESOURCE)
                .fetchDocumentTypes();
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

    private SafeHtml getUUID(final Dependency row,
                             final Function<Dependency, DocRef> docRefExtractor) {

        final DocRef docRef = docRefExtractor.apply(row);
        final String uuid = docRef != null
                ? docRef.getUuid()
                : null;

        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<span style=\"color:grey\">");
        builder.appendEscaped(uuid);
        builder.appendHtmlConstant("</span>");
        return builder.toSafeHtml();
    }

    private Preset getDocTypeIcon(final DocRef docRef) {
        if (docRef != null && docRef.getType() != null && !docRef.getType().isEmpty()) {
            final Preset svgPreset = typeToSvgMap.get(docRef.getType());
            if (svgPreset != null) {
                return svgPreset;
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
        getView().getDataDisplay().setVisibleRange(new Range(0, DEFAULT_PAGE_SIZE));
    }

    void refresh() {
        this.dataProvider.refresh();
    }
}
