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

package stroom.query.client.presenter;

import stroom.annotation.shared.AnnotationDecorationFields;
import stroom.dashboard.client.main.UniqueUtil;
import stroom.docref.DocRef;
import stroom.item.client.SelectionItem;
import stroom.item.client.SelectionListModel;
import stroom.query.api.Column;
import stroom.query.api.Column.Builder;
import stroom.query.api.Format;
import stroom.query.api.ParamUtil;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.DataSourceClient;
import stroom.query.client.presenter.DynamicColumnSelectionListModel.ColumnSelectionItem;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.HasTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class DynamicColumnSelectionListModel
        implements SelectionListModel<Column, ColumnSelectionItem>, HasTaskMonitorFactory, HasHandlers {

    private static final String NONE_TITLE = "[ none ]";

    private final EventBus eventBus;
    private final DataSourceClient dataSourceClient;
    private final ClientSecurityContext clientSecurityContext;
    private DocRef dataSourceRef;
    private FindFieldCriteria lastCriteria;
    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);

    @Inject
    public DynamicColumnSelectionListModel(final EventBus eventBus,
                                           final DataSourceClient dataSourceClient,
                                           final ClientSecurityContext clientSecurityContext) {
        this.eventBus = eventBus;
        this.dataSourceClient = dataSourceClient;
        this.clientSecurityContext = clientSecurityContext;
    }

    @Override
    public void onRangeChange(final ColumnSelectionItem parent,
                              final String filter,
                              final boolean filterChange,
                              final PageRequest pageRequest,
                              final Consumer<ResultPage<ColumnSelectionItem>> consumer) {
        final String parentPath = getParentPath(parent);
        if (dataSourceRef != null) {
            final FindFieldCriteria findFieldInfoCriteria = new FindFieldCriteria(
                    pageRequest,
                    FindFieldCriteria.DEFAULT_SORT_LIST,
                    dataSourceRef,
                    filter,
                    null);

            // Only fetch if the request has changed.
            lastCriteria = findFieldInfoCriteria;

            dataSourceClient.findFields(findFieldInfoCriteria, response -> {
                // Only update if the request is still current.
                if (findFieldInfoCriteria == lastCriteria) {
                    final ResultPage<ColumnSelectionItem> resultPage =
                            createResults(filter, parentPath, pageRequest, response);
                    consumer.accept(resultPage);
                }
            }, taskMonitorFactory);
        }
    }

    private String getParentPath(final ColumnSelectionItem parent) {
        String parentPath = "";
        if (parent != null) {
            if (parent.getColumn() != null) {
                parentPath = "Data Source" + ".";
            } else {
                parentPath = parent.getLabel() + ".";
            }
        }
        return parentPath;
    }

    private ResultPage<ColumnSelectionItem> createResults(final String filter,
                                                          final String parentPath,
                                                          final PageRequest pageRequest,
                                                          final ResultPage<QueryField> response) {
        final ResultPage<ColumnSelectionItem> counts = getCounts(filter, pageRequest);
        final ResultPage<ColumnSelectionItem> custom = getCustom(filter, pageRequest);
        final ResultPage<ColumnSelectionItem> annotations = getAnnotations(filter, pageRequest);

        ResultPage<ColumnSelectionItem> resultPage = null;
        if (NullSafe.isBlankString(parentPath)) {
            final ExactResultPageBuilder<ColumnSelectionItem> builder = new ExactResultPageBuilder<>(pageRequest);
            add(filter, new ColumnSelectionItem(
                    null,
                    "Annotations",
                    !annotations.isEmpty()), builder);
            add(filter, new ColumnSelectionItem(
                    null,
                    "Counts",
                    !counts.isEmpty()), builder);
            add(filter, new ColumnSelectionItem(
                    null,
                    "Custom",
                    !custom.isEmpty()), builder);
            add(filter, new ColumnSelectionItem(
                    null,
                    "Data Source",
                    !response.getValues().isEmpty()), builder);

            resultPage = builder.build();
        } else if ("Counts.".equals(parentPath)) {
            resultPage = counts;
        } else if ("Custom.".equals(parentPath)) {
            resultPage = custom;
//        } else if ("Annotation Links.".equals(parentPath)) {
//            resultPage = annotationLinks;
        } else if ("Annotations.".equals(parentPath)) {
            resultPage = annotations;
        } else if ("Data Source.".equals(parentPath)) {
            final List<ColumnSelectionItem> items = response
                    .getValues()
                    .stream()
                    .map(ColumnSelectionItem::create)
                    .collect(Collectors.toList());
            resultPage = new ResultPage<>(items, response.getPageResponse());
        }

        if (resultPage == null || resultPage.getValues().isEmpty()) {
            resultPage = new ResultPage<>(Collections.singletonList(
                    new ColumnSelectionItem(null, NONE_TITLE, false)),
                    new PageResponse(0, 1, 1L, true));
        }

        return resultPage;
    }

    private ResultPage<ColumnSelectionItem> getCounts(final String filter,
                                                      final PageRequest pageRequest) {
        final ExactResultPageBuilder<ColumnSelectionItem> builder = new ExactResultPageBuilder<>(pageRequest);
        final Column count = Column.builder()
                .id(UniqueUtil.generateUUID())
                .name("Count")
                .format(Format.NUMBER)
                .expression("count()")
                .build();
        add(filter, ColumnSelectionItem.create(count), builder);
        final Column countGroups = Column.builder()
                .id(UniqueUtil.generateUUID())
                .name("Count Groups")
                .format(Format.NUMBER)
                .expression("countGroups()")
                .build();
        add(filter, ColumnSelectionItem.create(countGroups), builder);
        return builder.build();
    }

    private ResultPage<ColumnSelectionItem> getCustom(final String filter,
                                                      final PageRequest pageRequest) {
        final ExactResultPageBuilder<ColumnSelectionItem> builder = new ExactResultPageBuilder<>(pageRequest);
        final Column custom = Column.builder()
                .id(UniqueUtil.generateUUID())
                .name("Custom")
                .build();
        add(filter, ColumnSelectionItem.create(custom), builder);
        return builder.build();
    }

    private ResultPage<ColumnSelectionItem> getAnnotations(final String filter,
                                                           final PageRequest pageRequest) {
        final ExactResultPageBuilder<ColumnSelectionItem> builder = new ExactResultPageBuilder<>(pageRequest);
        if (dataSourceRef != null &&
            dataSourceRef.getType() != null &&
            clientSecurityContext.hasAppPermission(AppPermission.ANNOTATIONS)) {
            if ("Index".equals(dataSourceRef.getType()) ||
                "SolrIndex".equals(dataSourceRef.getType()) ||
                "ElasticIndex".equals(dataSourceRef.getType())) {
                AnnotationDecorationFields.DECORATION_FIELDS.forEach(field -> {
                    final ColumnSelectionItem columnSelectionItem = ColumnSelectionItem.create(field);
                    add(filter, columnSelectionItem, builder);
                });
            }
        }
        return builder.build();
    }

    private void add(final String filter,
                     final ColumnSelectionItem item,
                     final ExactResultPageBuilder<ColumnSelectionItem> resultPageBuilder) {
        if (item.isHasChildren()) {
            resultPageBuilder.add(item);
        } else if (NullSafe.isNonBlankString(filter)) {
            if (item.getLabel().toLowerCase().contains(filter.toLowerCase(Locale.ROOT))) {
                resultPageBuilder.add(item);
            }
        } else {
            resultPageBuilder.add(item);
        }
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    @Override
    public void reset() {
        lastCriteria = null;
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

    @Override
    public boolean isEmptyItem(final ColumnSelectionItem selectionItem) {
        return NONE_TITLE.equals(selectionItem.getLabel());
    }

    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
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

        public static ColumnSelectionItem create(final QueryField fieldInfo) {
            final Column column = convertFieldInfo(fieldInfo);
            return new ColumnSelectionItem(column, column.getDisplayValue(), false);
        }

        private static String buildAnnotationFieldExpression(final FieldType fieldType,
                                                             final String indexFieldName) {
            return ParamUtil.create(indexFieldName);
        }

        private static Column convertFieldInfo(final QueryField queryField) {
            final String indexFieldName = queryField.getFldName();
            final Builder columnBuilder = Column.builder();
            columnBuilder.id(UniqueUtil.generateUUID());
            columnBuilder.name(indexFieldName);
            columnBuilder.format(Format.GENERAL);

            final FieldType fieldType = queryField.getFldType();
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

            columnBuilder.expression(ParamUtil.create(indexFieldName));

            // Make annotation column names more readable.
            if (indexFieldName.startsWith(AnnotationDecorationFields.ANNOTATION_FIELD_PREFIX)) {
                String columnName = indexFieldName.substring(
                        AnnotationDecorationFields.ANNOTATION_FIELD_PREFIX.length());
                columnName = columnName.replaceAll("([A-Z])", " $1");
                columnName = columnName.replaceAll("Uuid", "UUID");
                columnName = "Annotation " + columnName.trim();
                columnBuilder.name(columnName);
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
            return hasChildren == that.hasChildren &&
                   Objects.equals(column, that.column) &&
                   Objects.equals(label, that.label);
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
