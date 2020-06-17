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

import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerResource;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.client.ImageUtil;
import stroom.util.shared.ResultPage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DependenciesPresenter extends MyPresenterWidget<DataGridView<Dependency>> {

    private static final ContentResource CONTENT_RESOURCE = GWT.create(ContentResource.class);
    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private static final int COL_WIDTH_TYPE = 120;
    private static final int COL_WIDTH_NAME = 300;
    private static final int COL_WIDTH_UUID = 270;

    private final RestFactory restFactory;
    private final DependencyCriteria criteria;
    private final RestDataProvider<Dependency, ResultPage<Dependency>> dataProvider;

    // Holds all the doc type icons
    private Map<String, SvgPreset> typeToSvgMap = new HashMap<>();

    @Inject
    public DependenciesPresenter(final EventBus eventBus, final RestFactory restFactory) {
        super(eventBus, new DataGridViewImpl<>(false, 100));
        this.restFactory = restFactory;
        criteria = new DependencyCriteria();

        refreshDocTypeIcons();

        dataProvider = new RestDataProvider<Dependency, ResultPage<Dependency>>(
                eventBus,
                criteria.obtainPageRequest()) {
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

        // From (Icon)
        getView().addColumn(DataGridUtil.svgPresetColumnBuilder((Dependency row) ->
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
        getView().addResizableColumn(DataGridUtil.textColumnBuilder((Dependency row) ->
                        getValue(row, Dependency::getFrom, DocRef::getName))
                        .withSorting(DependencyCriteria.FIELD_FROM_NAME, true)
                        .build(),
                DependencyCriteria.FIELD_FROM_NAME,
                COL_WIDTH_NAME);

        // From (UUID)
        getView().addResizableColumn(DataGridUtil.htmlColumnBuilder((Dependency row) ->
                        getUUID(row, Dependency::getFrom))
                        .build(),
                DependencyCriteria.FIELD_FROM_UUID,
                COL_WIDTH_UUID);

        // To (Icon)
        getView().addColumn(DataGridUtil.svgPresetColumnBuilder((Dependency row) ->
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
        getView().addResizableColumn(DataGridUtil.textColumnBuilder((Dependency row) ->
                        getValue(row, Dependency::getTo, DocRef::getName))
                        .withSorting(DependencyCriteria.FIELD_TO_NAME, true)
                        .build(),
                DependencyCriteria.FIELD_TO_NAME,
                COL_WIDTH_NAME);

        // To (UUID)
        getView().addResizableColumn(DataGridUtil.htmlColumnBuilder((Dependency row) ->
                        getUUID(row, Dependency::getFrom))
                        .build(),
                DependencyCriteria.FIELD_TO_UUID,
                COL_WIDTH_UUID);

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
        final String uuid = docRef != null ? docRef.getUuid() : null;

        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<span style=\"color:grey\">");
        builder.appendEscaped(uuid);
        builder.appendHtmlConstant("</span>");
        return builder.toSafeHtml();
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
    }

    void clearFilterInput() {
        this.criteria.setPartialName(null);
    }

    void refresh() {
        this.dataProvider.refresh();
    }
}
