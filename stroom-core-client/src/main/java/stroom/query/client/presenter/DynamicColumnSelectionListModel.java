package stroom.query.client.presenter;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.datasource.shared.DataSourceResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.item.client.NavigationModel;
import stroom.item.client.SelectionItem;
import stroom.item.client.SelectionListModel;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Field.Builder;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.ParamSubstituteUtil;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.StringCriteria;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.AbstractDataProvider;
import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class DynamicColumnSelectionListModel implements SelectionListModel {


    private static final DataSourceResource DATA_SOURCE_RESOURCE = GWT.create(DataSourceResource.class);

    private final RestFactory restFactory;
    private final AsyncDataProvider<SelectionItem> dataProvider;
    private final NavigationModel navigationModel = new NavigationModel();
    private DocRef dataSourceRef;
    private StringMatch filter;

    @Inject
    public DynamicColumnSelectionListModel(final RestFactory restFactory) {
        this.restFactory = restFactory;

        dataProvider = new AsyncDataProvider<SelectionItem>() {
            @Override
            protected void onRangeChanged(final HasData<SelectionItem> display) {
                refresh(display);
            }
        };
    }

    private void refresh(final HasData<SelectionItem> display) {
        if (dataSourceRef != null) {
            final Range range = display.getVisibleRange();
            final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
            String parentPath = "";
            if (!navigationModel.getPath().isEmpty()) {
                final SelectionItem lastItem = navigationModel.getPath().peek();
                final ColumnSelectionItem columnSelectionItem = (ColumnSelectionItem) lastItem;
                if (columnSelectionItem.getField() != null) {
                    parentPath = FieldInfo.FIELDS_PARENT + columnSelectionItem.getField().getName();
                } else {
                    parentPath = columnSelectionItem.getLabel() + ".";
                }
            }

            if (GwtNullSafe.isBlankString(parentPath)) {
                final List<ColumnSelectionItem> list = new ArrayList<>();
                list.add(ColumnSelectionItem.createParent("Counts"));
                list.add(ColumnSelectionItem.createParent("Data Source"));
                list.add(ColumnSelectionItem.createParent("Annotations"));

                display.setRowData(0, list);
                display.setRowCount(list.size(), true);

            } else if ("counts.".equals(parentPath)) {
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

            } else if ("annotations.".equals(parentPath)) {
                final FindFieldInfoCriteria findFieldInfoCriteria = new FindFieldInfoCriteria(
                        pageRequest,
                        null,
                        dataSourceRef,
                        FieldInfo.FIELDS_PARENT,
                        StringMatch.contains("annotation:"));
                final Rest<ResultPage<FieldInfo>> rest = restFactory.create();
                rest
                        .onSuccess(result -> {
                            final List<SelectionItem> items = result
                                    .getValues()
                                    .stream()
                                    .map(ColumnSelectionItem::create)
                                    .collect(Collectors.toList());
                            display.setRowData(0, items);
                            display.setRowCount(result.getPageResponse().getLength(), true);
                        })
                        .call(DATA_SOURCE_RESOURCE)
                        .findFields(findFieldInfoCriteria);

            } else if (FieldInfo.FIELDS_PARENT.equals(parentPath)) {
                final FindFieldInfoCriteria findFieldInfoCriteria = new FindFieldInfoCriteria(
                        pageRequest,
                        null,
                        dataSourceRef,
                        FieldInfo.FIELDS_PARENT,
                        filter);
                final Rest<ResultPage<FieldInfo>> rest = restFactory.create();
                rest
                        .onSuccess(result -> {
                            final List<SelectionItem> items = result
                                    .getValues()
                                    .stream()
                                    .map(ColumnSelectionItem::create)
                                    .collect(Collectors.toList());
                            display.setRowData(0, items);
                            display.setRowCount(result.getPageResponse().getLength(), true);
                        })
                        .call(DATA_SOURCE_RESOURCE)
                        .findFields(findFieldInfoCriteria);
            }
        }
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    @Override
    public AbstractDataProvider<SelectionItem> getDataProvider() {
        return dataProvider;
    }

    @Override
    public NavigationModel getNavigationModel() {
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
        for (final HasData<SelectionItem> display : dataProvider.getDataDisplays()) {
            refresh(display);
        }
    }

    @Override
    public boolean displayPath() {
        return true;
    }

    @Override
    public boolean displayPager() {
        return true;
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

        private static String buildAnnotationFieldExpression(final AbstractField dataSourceField,
                                                             final String indexFieldName) {
            String fieldParam = ParamSubstituteUtil.makeParam(indexFieldName);
            if (dataSourceField != null && FieldType.DATE.equals(dataSourceField.getFieldType())) {
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
            final String indexFieldName = fieldInfo.getTitle();
            final Builder fieldBuilder = Field.builder();
            fieldBuilder.name(indexFieldName);

            final AbstractField indexField = fieldInfo.getField();
            if (indexField != null) {
                switch (indexField.getFieldType()) {
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
                expression = buildAnnotationFieldExpression(fieldInfo.getField(), indexFieldName);
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
    }
}
