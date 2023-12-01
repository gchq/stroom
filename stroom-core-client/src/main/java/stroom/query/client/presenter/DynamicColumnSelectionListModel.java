package stroom.query.client.presenter;

import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.docref.StringMatch.MatchType;
import stroom.item.client.NavigationModel;
import stroom.item.client.SelectionItem;
import stroom.item.client.SelectionListModel;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.Column.Builder;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.ParamSubstituteUtil;
import stroom.query.client.DataSourceClient;
import stroom.query.client.presenter.DynamicColumnSelectionListModel.ColumnSelectionItem;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.google.gwt.view.client.AbstractDataProvider;
import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class DynamicColumnSelectionListModel implements SelectionListModel<Column, ColumnSelectionItem> {

    private final DataSourceClient dataSourceClient;
    private final ClientSecurityContext clientSecurityContext;
    private final AsyncDataProvider<ColumnSelectionItem> dataProvider;
    private final NavigationModel<ColumnSelectionItem> navigationModel = new NavigationModel<>();
    private DocRef dataSourceRef;
    private StringMatch filter;
    private FindFieldInfoCriteria lastCriteria;
    private String lastPath;

    @Inject
    public DynamicColumnSelectionListModel(final DataSourceClient dataSourceClient,
                                           final ClientSecurityContext clientSecurityContext) {
        this.dataSourceClient = dataSourceClient;
        this.clientSecurityContext = clientSecurityContext;
        dataProvider = new AsyncDataProvider<ColumnSelectionItem>() {
            @Override
            protected void onRangeChanged(final HasData<ColumnSelectionItem> display) {
                refresh(display);
            }
        };
    }

    private void refresh(final HasData<ColumnSelectionItem> display) {
        final String parentPath = getParentPath();
        if (dataSourceRef != null) {
            final Range range = display.getVisibleRange();
            final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());

            final FindFieldInfoCriteria findFieldInfoCriteria = new FindFieldInfoCriteria(
                    pageRequest,
                    null,
                    dataSourceRef,
                    filter);

            // Only fetch if the request has changed.
            if (!parentPath.equals(lastPath) || !findFieldInfoCriteria.equals(lastCriteria)) {
                lastPath = parentPath;
                lastCriteria = findFieldInfoCriteria;

                dataSourceClient.findFields(findFieldInfoCriteria, response -> {
                    // Only update if the request is still current.
                    if (findFieldInfoCriteria == lastCriteria) {
                        setResponse(parentPath, pageRequest, response, display);
                    }
                });
            }
        }
    }

    private String getParentPath() {
        String parentPath = "";
        if (!navigationModel.getPath().isEmpty()) {
            final ColumnSelectionItem lastItem = navigationModel.getPath().peek();
            if (lastItem.getColumn() != null) {
                parentPath = "Data Source" + ".";
            } else {
                parentPath = lastItem.getLabel() + ".";
            }
        }
        return parentPath;
    }

    private void setResponse(final String parentPath,
                             final PageRequest pageRequest,
                             final ResultPage<FieldInfo> response,
                             final HasData<ColumnSelectionItem> display) {
        final ResultPage<ColumnSelectionItem> counts = getCounts(pageRequest);
        final ResultPage<ColumnSelectionItem> annotations = getAnnotations(pageRequest);

        if (GwtNullSafe.isBlankString(parentPath)) {
            final ExactResultPageBuilder<ColumnSelectionItem> builder = new ExactResultPageBuilder<>(pageRequest);
            add(new ColumnSelectionItem(null, "Annotations", annotations.size() > 0), builder);
            add(new ColumnSelectionItem(null, "Counts", counts.size() > 0), builder);
            add(new ColumnSelectionItem(null, "Data Source", response.getValues().size() > 0), builder);

            final ResultPage<ColumnSelectionItem> resultPage = builder.build();
            display.setRowData(resultPage.getPageStart(), resultPage.getValues());
            display.setRowCount(resultPage.getPageSize(), resultPage.isExact());

        } else if ("Counts.".equals(parentPath)) {
            display.setRowData(counts.getPageStart(), counts.getValues());
            display.setRowCount(counts.getPageSize(), counts.isExact());

        } else if ("Annotations.".equals(parentPath)) {
            display.setRowData(annotations.getPageStart(), annotations.getValues());
            display.setRowCount(annotations.getPageSize(), annotations.isExact());

        } else if ("Data Source.".equals(parentPath)) {
            final List<ColumnSelectionItem> items = response
                    .getValues()
                    .stream()
                    .map(ColumnSelectionItem::create)
                    .collect(Collectors.toList());
            display.setRowData(response.getPageStart(), items);
            display.setRowCount(response.getPageSize(), response.isExact());
        }
    }

    private ResultPage<ColumnSelectionItem> getCounts(final PageRequest pageRequest) {
        final ExactResultPageBuilder<ColumnSelectionItem> builder = new ExactResultPageBuilder<>(pageRequest);
        final Column count = Column.builder()
                .name("Count")
                .format(Format.NUMBER)
                .expression("count()")
                .build();
        add(ColumnSelectionItem.create(count), builder);
        final Column countGroups = Column.builder()
                .name("Count Groups")
                .format(Format.NUMBER)
                .expression("countGroups()")
                .build();
        add(ColumnSelectionItem.create(countGroups), builder);
        final Column custom = Column.builder()
                .name("Custom")
                .build();
        add(ColumnSelectionItem.create(custom), builder);
        return builder.build();
    }

    private ResultPage<ColumnSelectionItem> getAnnotations(final PageRequest pageRequest) {
        final ExactResultPageBuilder<ColumnSelectionItem> builder = new ExactResultPageBuilder<>(pageRequest);
        if (dataSourceRef != null &&
                dataSourceRef.getType() != null &&
                clientSecurityContext.hasAppPermission(PermissionNames.ANNOTATIONS)) {
            if ("Index".equals(dataSourceRef.getType()) ||
                    "SolrIndex".equals(dataSourceRef.getType()) ||
                    "ElasticIndex".equals(dataSourceRef.getType())) {
                AnnotationFields.FIELDS.forEach(field -> {
                    final FieldInfo fieldInfo = FieldInfo.create(field);
                    final ColumnSelectionItem columnSelectionItem = ColumnSelectionItem.create(fieldInfo);
                    add(columnSelectionItem, builder);
                });
            }
        }
        return builder.build();
    }

    private void add(final ColumnSelectionItem item,
                     final ExactResultPageBuilder<ColumnSelectionItem> resultPageBuilder) {
        if (item.isHasChildren()) {
            resultPageBuilder.add(item);
        } else if (filter != null && filter.getPattern() != null && MatchType.CONTAINS.equals(filter.getMatchType())) {
            if (item.getLabel().toLowerCase().contains(filter.getPattern().toLowerCase(Locale.ROOT))) {
                resultPageBuilder.add(item);
            }
        } else {
            resultPageBuilder.add(item);
        }
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    @Override
    public void reset() {
        navigationModel.reset();
        this.filter = StringMatch.any();
        lastCriteria = null;
        lastPath = null;
    }

    @Override
    public AbstractDataProvider<ColumnSelectionItem> getDataProvider() {
        return dataProvider;
    }

    @Override
    public NavigationModel<ColumnSelectionItem> getNavigationModel() {
        return navigationModel;
    }

    @Override
    public void setFilter(final String filter) {
        if (filter == null) {
            this.filter = StringMatch.any();
            refresh();
        } else {
            this.filter = StringMatch.contains(filter);
            refresh();
        }
    }

    @Override
    public void refresh() {
        for (final HasData<ColumnSelectionItem> display : dataProvider.getDataDisplays()) {
            refresh(display);
        }
    }

    @Override
    public String getPathRoot() {
        return "Columns";
    }

    @Override
    public boolean displayFilter() {
        return true;
    }

    @Override
    public boolean displayPath() {
        return true;
    }

    @Override
    public boolean displayPager() {
        return true;
    }

    @Override
    public ColumnSelectionItem wrap(final Column column) {
        return new ColumnSelectionItem(column, column.getDisplayValue(), false);
    }

    @Override
    public Column unwrap(final ColumnSelectionItem selectionItem) {
        if (selectionItem == null) {
            return null;
        }
        return selectionItem.getColumn();
    }

    public static class ColumnSelectionItem implements SelectionItem {

        private final Column column;
        private final String label;
        private final boolean hasChildren;

        public ColumnSelectionItem(final Column column,
                                   final String label,
                                   final boolean hasChildren) {
            this.column = column;
            this.label = label;
            this.hasChildren = hasChildren;
        }

        public static ColumnSelectionItem create(final Column column) {
            return new ColumnSelectionItem(column, column.getDisplayValue(), false);
        }

        public static ColumnSelectionItem create(final FieldInfo fieldInfo) {
            final Column column = convertFieldInfo(fieldInfo);
            return new ColumnSelectionItem(column, column.getDisplayValue(), false);
        }

        private static String buildAnnotationFieldExpression(final FieldType fieldType,
                                                             final String indexFieldName) {
            String fieldParam = ParamSubstituteUtil.makeParam(indexFieldName);
            if (FieldType.DATE.equals(fieldType)) {
                fieldParam = "formatDate(" + fieldParam + ")";
            }

            final List<String> params = new ArrayList<>();
            params.add(fieldParam);
            addFieldIfPresent(params, "annotation:Id");
            addFieldIfPresent(params, "StreamId");
            addFieldIfPresent(params, "EventId");

            final String argsStr = String.join(", ", params);
            return "annotation(" + argsStr + ")";
        }

        private static void addFieldIfPresent(final List<String> params,
                                              final String fieldName) {
            params.add(ParamSubstituteUtil.makeParam(fieldName));
        }

        private static Column convertFieldInfo(final FieldInfo fieldInfo) {
            final String indexFieldName = fieldInfo.getFieldName();
            final Builder columnBuilder = Column.builder();
            columnBuilder.name(indexFieldName);

            final FieldType fieldType = fieldInfo.getFieldType();
            if (fieldType != null) {
                switch (fieldType) {
                    case DATE:
                        columnBuilder.format(Format.DATE_TIME);
                        break;
                    case INTEGER:
                    case LONG:
                    case FLOAT:
                    case DOUBLE:
                    case ID:
                        columnBuilder.format(Format.NUMBER);
                        break;
                    default:
                        columnBuilder.format(Format.GENERAL);
                        break;
                }
            }

            final String expression;
            if (indexFieldName.startsWith("annotation:")) {
                // Turn 'annotation:.*' fields into annotation links that make use of either the special
                // eventId/streamId fields (so event results can link back to annotations) OR
                // the annotation:Id field so Annotations datasource results can link back.
                expression = buildAnnotationFieldExpression(fieldInfo.getFieldType(), indexFieldName);
                columnBuilder.expression(expression);
            } else {
                expression = ParamSubstituteUtil.makeParam(indexFieldName);
                columnBuilder.expression(expression);
            }

            return columnBuilder.build();
        }

        @Override
        public String getLabel() {
            return label;
        }

        @Override
        public SvgImage getIcon() {
            return null;
        }

        @Override
        public boolean isHasChildren() {
            return hasChildren;
        }

        public Column getColumn() {
            return column;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ColumnSelectionItem)) {
                return false;
            }
            final ColumnSelectionItem that = (ColumnSelectionItem) o;
            return hasChildren == that.hasChildren && Objects.equals(column, that.column) && Objects.equals(
                    label,
                    that.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(column, label, hasChildren);
        }

        @Override
        public String toString() {
            return "ColumnSelectionItem{" +
                    "column=" + column +
                    ", label='" + label + '\'' +
                    ", hasChildren=" + hasChildren +
                    '}';
        }
    }
}
