package stroom.query.client.presenter;

import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.docref.StringMatch.MatchType;
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
import stroom.task.client.HasTaskListener;
import stroom.task.client.TaskListener;
import stroom.task.client.TaskListenerImpl;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class DynamicColumnSelectionListModel
        implements SelectionListModel<Column, ColumnSelectionItem>, HasTaskListener, HasHandlers {

    private static final String NONE_TITLE = "[ none ]";

    private final EventBus eventBus;
    private final DataSourceClient dataSourceClient;
    private final ClientSecurityContext clientSecurityContext;
    private DocRef dataSourceRef;
    private FindFieldCriteria lastCriteria;
    private final TaskListenerImpl taskListener = new TaskListenerImpl(this);

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
            final StringMatch stringMatch = StringMatch.containsIgnoreCase(filter);
            final FindFieldCriteria findFieldInfoCriteria = new FindFieldCriteria(
                    pageRequest,
                    null,
                    dataSourceRef,
                    stringMatch,
                    null);

            // Only fetch if the request has changed.
            lastCriteria = findFieldInfoCriteria;

            dataSourceClient.findFields(findFieldInfoCriteria, response -> {
                // Only update if the request is still current.
                if (findFieldInfoCriteria == lastCriteria) {
                    final ResultPage<ColumnSelectionItem> resultPage =
                            createResults(stringMatch, parentPath, pageRequest, response);
                    consumer.accept(resultPage);
                }
            }, taskListener);
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

    private ResultPage<ColumnSelectionItem> createResults(final StringMatch filter,
                                                          final String parentPath,
                                                          final PageRequest pageRequest,
                                                          final ResultPage<QueryField> response) {
        final ResultPage<ColumnSelectionItem> counts = getCounts(filter, pageRequest);
        final ResultPage<ColumnSelectionItem> annotations = getAnnotations(filter, pageRequest);

        ResultPage<ColumnSelectionItem> resultPage = null;
        if (GwtNullSafe.isBlankString(parentPath)) {
            final ExactResultPageBuilder<ColumnSelectionItem> builder = new ExactResultPageBuilder<>(pageRequest);
            add(filter, new ColumnSelectionItem(
                    null,
                    "Annotations",
                    annotations.size() > 0), builder);
            add(filter, new ColumnSelectionItem(
                    null,
                    "Counts",
                    counts.size() > 0), builder);
            add(filter, new ColumnSelectionItem(
                    null,
                    "Data Source",
                    !response.getValues().isEmpty()), builder);

            resultPage = builder.build();
        } else if ("Counts.".equals(parentPath)) {
            resultPage = counts;
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

    private ResultPage<ColumnSelectionItem> getCounts(final StringMatch filter,
                                                      final PageRequest pageRequest) {
        final ExactResultPageBuilder<ColumnSelectionItem> builder = new ExactResultPageBuilder<>(pageRequest);
        final Column count = Column.builder()
                .name("Count")
                .format(Format.NUMBER)
                .expression("count()")
                .build();
        add(filter, ColumnSelectionItem.create(count), builder);
        final Column countGroups = Column.builder()
                .name("Count Groups")
                .format(Format.NUMBER)
                .expression("countGroups()")
                .build();
        add(filter, ColumnSelectionItem.create(countGroups), builder);
        final Column custom = Column.builder()
                .name("Custom")
                .build();
        add(filter, ColumnSelectionItem.create(custom), builder);
        return builder.build();
    }

    private ResultPage<ColumnSelectionItem> getAnnotations(final StringMatch filter,
                                                           final PageRequest pageRequest) {
        final ExactResultPageBuilder<ColumnSelectionItem> builder = new ExactResultPageBuilder<>(pageRequest);
        if (dataSourceRef != null &&
                dataSourceRef.getType() != null &&
                clientSecurityContext.hasAppPermission(PermissionNames.ANNOTATIONS)) {
            if ("Index".equals(dataSourceRef.getType()) ||
                    "SolrIndex".equals(dataSourceRef.getType()) ||
                    "ElasticIndex".equals(dataSourceRef.getType())) {
                AnnotationFields.FIELDS.forEach(field -> {
                    final ColumnSelectionItem columnSelectionItem = ColumnSelectionItem.create(field);
                    add(filter, columnSelectionItem, builder);
                });
            }
        }
        return builder.build();
    }

    private void add(final StringMatch filter,
                     final ColumnSelectionItem item,
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
    public void setTaskListener(final TaskListener taskListener) {
        this.taskListener.setTaskListener(taskListener);
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

        private static Column convertFieldInfo(final QueryField fieldInfo) {
            final String indexFieldName = fieldInfo.getFldName();
            final Builder columnBuilder = Column.builder();
            columnBuilder.name(indexFieldName);

            final FieldType fieldType = fieldInfo.getFldType();
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
                expression = buildAnnotationFieldExpression(fieldInfo.getFldType(), indexFieldName);
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
