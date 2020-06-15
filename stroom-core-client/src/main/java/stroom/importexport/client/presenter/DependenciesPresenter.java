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

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.OrderByColumn;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerResource;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.client.ImageUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Sort.Direction;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DependenciesPresenter extends ContentTabPresenter<DataGridView<Dependency>> implements ColumnSortEvent.Handler {
    private static final ContentResource CONTENT_RESOURCE = GWT.create(ContentResource.class);
    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final RestFactory restFactory;
    private final DependencyCriteria criteria;
    private final RestDataProvider<Dependency, ResultPage<Dependency>> dataProvider;
    private Map<String, SvgPreset> typeToSvgMap = new HashMap<>();

    @Inject
    public DependenciesPresenter(final EventBus eventBus, final RestFactory restFactory) {
        super(eventBus, new DataGridViewImpl<>(false, 100));
        this.restFactory = restFactory;
        criteria = new DependencyCriteria();

        refreshDocTypeIcons();

        dataProvider = new RestDataProvider<Dependency, ResultPage<Dependency>>(eventBus, criteria.obtainPageRequest()) {
            @Override
            protected void exec(final Consumer<ResultPage<Dependency>> dataConsumer,
                                final Consumer<Throwable> throwableConsumer) {
                final Rest<ResultPage<Dependency>> rest = restFactory.create();
                rest
                        .onSuccess(dataConsumer)
                        .onFailure(throwableConsumer)
                        .call(CONTENT_RESOURCE)
                        .fetchDependencies(criteria);
            }
        };
        dataProvider.addDataDisplay(getView().getDataDisplay());
        initColumns();
    }

    private void initColumns() {

        getView().addColumn(DataGridUtil.svgPresetColumnBuilder((Dependency row) -> getDocTypeIcon(row.getFrom()))
                        .build(),
                "<br/>",
                ColumnSizeConstants.ICON_COL);

        getView().addResizableColumn(DataGridUtil.htmlColumnBuilder((Dependency row) -> docRefToString(row.getFrom()))
                        .withSorting(DependencyCriteria.FIELD_FROM, true)
                        .build(),
                DependencyCriteria.FIELD_FROM,
                500);

        getView().addColumn(DataGridUtil.svgPresetColumnBuilder((Dependency row) -> getDocTypeIcon(row.getTo()))
                        .build(),
                "<br/>",
                ColumnSizeConstants.ICON_COL);

        getView().addResizableColumn(DataGridUtil.htmlColumnBuilder((Dependency row) -> docRefToString(row.getTo()))
                        .withSorting(DependencyCriteria.FIELD_TO, true)
                        .build(),
                DependencyCriteria.FIELD_TO,
                500);

        getView().addResizableColumn(DataGridUtil.htmlColumnBuilder(this::getStatusValue)
                        .withSorting(DependencyCriteria.FIELD_STATUS, false)
                        .build(),
                DependencyCriteria.FIELD_STATUS,
                100);

        DataGridUtil.addEndColumn(getView());

        DataGridUtil.addColumnSortHandler(getView(), criteria, dataProvider::refresh);
    }

    private void refreshDocTypeIcons() {

        // Hold map of doc type icons keyed on type to save constructing for each row
        final Rest<DocumentTypes> rest = restFactory.create();
        rest
                .onSuccess(documentTypes ->
                        typeToSvgMap = documentTypes.getVisibleTypes().stream()
                                .collect(Collectors.toMap(
                                        DocumentType::getType,
                                        documentType ->
                                                new SvgPreset(
                                                        ImageUtil.getImageURL() + documentType.getIconUrl(),
                                                        documentType.getDisplayType(),
                                                        true))))
                .call(EXPLORER_RESOURCE)
                .fetchDocumentTypes();
    }

    private SafeHtml docRefToString(final DocRef docRef) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendEscaped(docRef.getType());
//        builder.appendEscaped(" ");
        if (docRef.getName() != null && !docRef.getName().isEmpty()) {
            builder.appendEscaped(" - ");
            builder.appendEscaped(docRef.getName());
        }
        // UUIDs in grey to make the rest easier to read
        builder.appendHtmlConstant("<span style=\"color:grey\">");
        builder.appendEscaped(" {");
        builder.appendEscaped(docRef.getUuid());
        builder.appendEscaped("}");
        builder.appendHtmlConstant("</span>");
        return (builder.toSafeHtml());
    }

    private SvgPreset getDocTypeIcon(final DocRef docRef) {
        if (docRef != null && docRef.getType() != null && !docRef.getType().isEmpty()) {
            final SvgPreset svgPreset = typeToSvgMap.get(docRef.getType());
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
        if (row.isOk()) {
            value = "OK";
            builder.appendHtmlConstant("<span style=\"color:green; font-weight:bold\">");
        } else {
            value = "Missing";
            builder.appendHtmlConstant("<span style=\"color:red; font-weight:bold\">");
        }
        builder.appendEscaped(value);
        builder.appendHtmlConstant("</span>");
        return (builder.toSafeHtml());
    }

    @Override
    public void onColumnSort(final ColumnSortEvent event) {
        if (event.getColumn() instanceof OrderByColumn<?, ?>) {
            final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
            if (criteria != null) {
                if (event.isSortAscending()) {
                    criteria.setSort(orderByColumn.getField(), Direction.ASCENDING, orderByColumn.isIgnoreCase());
                } else {
                    criteria.setSort(orderByColumn.getField(), Direction.DESCENDING, orderByColumn.isIgnoreCase());
                }
                dataProvider.refresh();
            }
        }
    }

    @Override
    public Icon getIcon() {
        return SvgPresets.DEPENDENCIES;
    }

    @Override
    public String getLabel() {
        return "Dependencies";
    }
}
