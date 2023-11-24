package stroom.query.client.presenter;

import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.item.client.NavigationModel;
import stroom.item.client.SelectionItem;
import stroom.item.client.SelectionListModel;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.Column.Builder;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.ParamSubstituteUtil;
import stroom.query.client.DataSourceClient;
import stroom.query.client.presenter.DynamicColumnSelectionListModel.ColumnSelectionItem;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.PageRequest;

import com.google.gwt.view.client.AbstractDataProvider;
import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class DynamicColumnSelectionListModel implements SelectionListModel<Column, ColumnSelectionItem> {

    private final DataSourceClient dataSourceClient;
    private final AsyncDataProvider<ColumnSelectionItem> dataProvider;
    private final NavigationModel<ColumnSelectionItem> navigationModel = new NavigationModel<>();
    private DocRef dataSourceRef;
    private StringMatch filter;
    private FindFieldInfoCriteria lastCriteria;

    @Inject
    public DynamicColumnSelectionListModel(final DataSourceClient dataSourceClient) {
        this.dataSourceClient = dataSourceClient;
        dataProvider = new AsyncDataProvider<ColumnSelectionItem>() {
            @Override
            protected void onRangeChanged(final HasData<ColumnSelectionItem> display) {
                refresh(display);
            }
        };
    }

    private void refresh(final HasData<ColumnSelectionItem> display) {
        if (dataSourceRef != null) {
            final Range range = display.getVisibleRange();
            final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
            String parentPath = "";
            if (!navigationModel.getPath().isEmpty()) {
                final ColumnSelectionItem lastItem = navigationModel.getPath().peek();
                if (lastItem.getColumn() != null) {
                    parentPath = "Data Source" + ".";
                } else {
                    parentPath = lastItem.getLabel() + ".";
                }
            }

            if (GwtNullSafe.isBlankString(parentPath)) {
                lastCriteria = null;

                final List<ColumnSelectionItem> list = new ArrayList<>();
                list.add(ColumnSelectionItem.createParent("Annotations"));
                list.add(ColumnSelectionItem.createParent("Counts"));
                list.add(ColumnSelectionItem.createParent("Data Source"));

                display.setRowData(0, list);
                display.setRowCount(list.size(), true);

            } else if ("Counts.".equals(parentPath)) {
                lastCriteria = null;

                final List<ColumnSelectionItem> list = new ArrayList<>();
                final Column count = Column.builder()
                        .name("Count")
                        .format(Format.NUMBER)
                        .expression("count()")
                        .build();
                list.add(ColumnSelectionItem.create(count));

                final Column countGroups = Column.builder()
                        .name("Count Groups")
                        .format(Format.NUMBER)
                        .expression("countGroups()")
                        .build();
                list.add(ColumnSelectionItem.create(countGroups));

                final Column custom = Column.builder()
                        .name("Custom")
                        .build();
                list.add(ColumnSelectionItem.create(custom));

                display.setRowData(0, list);
                display.setRowCount(list.size(), true);

            } else if ("Annotations.".equals(parentPath)) {
                final FindFieldInfoCriteria findFieldInfoCriteria = new FindFieldInfoCriteria(
                        pageRequest,
                        null,
                        dataSourceRef,
                        StringMatch.contains("annotation:"));

                // Only fetch if the request has changed.
                if (!findFieldInfoCriteria.equals(lastCriteria)) {
                    lastCriteria = findFieldInfoCriteria;

                    dataSourceClient.findFields(findFieldInfoCriteria, response -> {
                        // Only update if the request is still current.
                        if (findFieldInfoCriteria == lastCriteria) {
                            final List<ColumnSelectionItem> items = response
                                    .getValues()
                                    .stream()
                                    .map(ColumnSelectionItem::create)
                                    .collect(Collectors.toList());
                            display.setRowData(response.getPageStart(), items);
                            display.setRowCount(response.getPageSize(), response.isExact());
                        }
                    });
                }

            } else if ("Data Source.".equals(parentPath)) {
                final FindFieldInfoCriteria findFieldInfoCriteria = new FindFieldInfoCriteria(
                        pageRequest,
                        null,
                        dataSourceRef,
                        filter);

                // Only fetch if the request has changed.
                if (!findFieldInfoCriteria.equals(lastCriteria)) {
                    lastCriteria = findFieldInfoCriteria;

                    dataSourceClient.findFields(findFieldInfoCriteria, response -> {
                        // Only update if the request is still current.
                        if (findFieldInfoCriteria == lastCriteria) {
                            final List<ColumnSelectionItem> items = response
                                    .getValues()
                                    .stream()
                                    .map(ColumnSelectionItem::create)
                                    .collect(Collectors.toList());
                            display.setRowData(response.getPageStart(), items);
                            display.setRowCount(response.getPageSize(), response.isExact());
                        }
                    });
                }
            }
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

        public static ColumnSelectionItem createParent(final String label) {
            return new ColumnSelectionItem(null, label, true);
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
