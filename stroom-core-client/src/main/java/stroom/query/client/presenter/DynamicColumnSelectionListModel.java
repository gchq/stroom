package stroom.query.client.presenter;

import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.item.client.NavigationModel;
import stroom.item.client.SelectionItem;
import stroom.item.client.SelectionListModel;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Field.Builder;
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

public class DynamicColumnSelectionListModel implements SelectionListModel<Field, ColumnSelectionItem> {

    private final DataSourceClient dataSourceClient;
    private final AsyncDataProvider<ColumnSelectionItem> dataProvider;
    private final NavigationModel<ColumnSelectionItem> navigationModel = new NavigationModel<>();
    private DocRef dataSourceRef;
    private StringMatch filter;

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
                if (lastItem.getField() != null) {
                    parentPath = "Data Source" + ".";
                } else {
                    parentPath = lastItem.getLabel() + ".";
                }
            }

            if (GwtNullSafe.isBlankString(parentPath)) {
                final List<ColumnSelectionItem> list = new ArrayList<>();
                list.add(ColumnSelectionItem.createParent("Annotations"));
                list.add(ColumnSelectionItem.createParent("Counts"));
                list.add(ColumnSelectionItem.createParent("Data Source"));

                display.setRowData(0, list);
                display.setRowCount(list.size(), true);

            } else if ("Counts.".equals(parentPath)) {
                final List<ColumnSelectionItem> list = new ArrayList<>();
                final Field count = Field.builder()
                        .name("Count")
                        .format(Format.NUMBER)
                        .expression("count()")
                        .build();
                list.add(ColumnSelectionItem.create(count));

                final Field countGroups = Field.builder()
                        .name("Count Groups")
                        .format(Format.NUMBER)
                        .expression("countGroups()")
                        .build();
                list.add(ColumnSelectionItem.create(countGroups));

                final Field custom = Field.builder()
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
                dataSourceClient.findFields(findFieldInfoCriteria, response -> {
                    final List<ColumnSelectionItem> items = response
                            .getValues()
                            .stream()
                            .map(ColumnSelectionItem::create)
                            .collect(Collectors.toList());
                    display.setRowData(response.getPageStart(), items);
                    display.setRowCount(response.getPageSize(), response.isExact());
                });

            } else if ("Data Source.".equals(parentPath)) {
                final FindFieldInfoCriteria findFieldInfoCriteria = new FindFieldInfoCriteria(
                        pageRequest,
                        null,
                        dataSourceRef,
                        filter);
                dataSourceClient.findFields(findFieldInfoCriteria, response -> {
                    final List<ColumnSelectionItem> items = response
                            .getValues()
                            .stream()
                            .map(ColumnSelectionItem::create)
                            .collect(Collectors.toList());
                    display.setRowData(response.getPageStart(), items);
                    display.setRowCount(response.getPageSize(), response.isExact());
                });
            }
        }
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    public void reset() {
        navigationModel.reset();
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
    public ColumnSelectionItem wrap(final Field item) {
        return new ColumnSelectionItem(item, item.getDisplayValue(), false);
    }

    @Override
    public Field unwrap(final ColumnSelectionItem selectionItem) {
        if (selectionItem == null) {
            return null;
        }
        return selectionItem.getField();
    }

    public static class ColumnSelectionItem implements SelectionItem {

        private final Field field;
        private final String label;
        private final boolean hasChildren;

        public ColumnSelectionItem(final Field field,
                                   final String label,
                                   final boolean hasChildren) {
            this.field = field;
            this.label = label;
            this.hasChildren = hasChildren;
        }

        public static ColumnSelectionItem createParent(final String label) {
            return new ColumnSelectionItem(null, label, true);
        }

        public static ColumnSelectionItem create(final Field field) {
            return new ColumnSelectionItem(field, field.getDisplayValue(), false);
        }

        public static ColumnSelectionItem create(final FieldInfo fieldInfo) {
            final Field field = convertFieldInfo(fieldInfo);
            return new ColumnSelectionItem(field, field.getDisplayValue(), false);
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

        private static Field convertFieldInfo(final FieldInfo fieldInfo) {
            final String indexFieldName = fieldInfo.getFieldName();
            final Builder fieldBuilder = Field.builder();
            fieldBuilder.name(indexFieldName);

            final FieldType fieldType = fieldInfo.getFieldType();
            if (fieldType != null) {
                switch (fieldType) {
                    case DATE:
                        fieldBuilder.format(Format.DATE_TIME);
                        break;
                    case INTEGER:
                    case LONG:
                    case FLOAT:
                    case DOUBLE:
                    case ID:
                        fieldBuilder.format(Format.NUMBER);
                        break;
                    default:
                        fieldBuilder.format(Format.GENERAL);
                        break;
                }
            }

            final String expression;
            if (indexFieldName.startsWith("annotation:")) {
                // Turn 'annotation:.*' fields into annotation links that make use of either the special
                // eventId/streamId fields (so event results can link back to annotations) OR
                // the annotation:Id field so Annotations datasource results can link back.
                expression = buildAnnotationFieldExpression(fieldInfo.getFieldType(), indexFieldName);
                fieldBuilder.expression(expression);
            } else {
                expression = ParamSubstituteUtil.makeParam(indexFieldName);
                fieldBuilder.expression(expression);
            }

            return fieldBuilder.build();

//        final Field count = Field.builder()
//                .name("Count")
//                .format(Format.NUMBER)
//                .expression("count()")
//                .build();
//        otherFields.add(count);
//
//        final Field countGroups = Field.builder()
//                .name("Count Groups")
//                .format(Format.NUMBER)
//                .expression("countGroups()")
//                .build();
//        otherFields.add(countGroups);
//
//        final Field custom = Field.builder()
//                .name("Custom")
//                .build();
//        otherFields.add(custom);
//
//        final SimpleSelectionBoxModel<Field> model = new SimpleSelectionBoxModel<>();
//        addFieldGroup(model, otherFields, "Counts");
//        addFieldGroup(model, dataFields, "Data Source");
//        addFieldGroup(model, annotationFields, "Annotations");
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

        public Field getField() {
            return field;
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
            return Objects.equals(field, that.field);
        }

        @Override
        public int hashCode() {
            return Objects.hash(field);
        }

        @Override
        public String toString() {
            return "ColumnSelectionItem{" +
                    "field=" + field +
                    ", label='" + label + '\'' +
                    ", hasChildren=" + hasChildren +
                    '}';
        }
    }
}
