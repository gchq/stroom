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

package stroom.receive.content.client.presenter;

import stroom.cell.info.client.ActionCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.DocRefCell;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.receive.content.shared.ContentTemplate;
import stroom.svg.client.Preset;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ContentTemplateListPresenter extends MyPresenterWidget<PagerView> implements Focus {

    private final MyDataGrid<ContentTemplate> dataGrid;
    private final MultiSelectionModelImpl<ContentTemplate> selectionModel;
    private BiConsumer<ContentTemplate, Boolean> enabledStateHandler;
    private Function<ContentTemplate, List<Item>> actionMenuItemProvider;
//    private List<ContentTemplate> currentData;

    @Inject
    public ContentTemplateListPresenter(final EventBus eventBus,
                                        final PagerView view) {
        super(eventBus, view);
        this.dataGrid = new MyDataGrid<>(this);
        this.selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);
        initTableColumns();
    }

    @Override
    public void focus() {
        dataGrid.setFocus(true);
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(ContentTemplate::isEnabled))
                        .enabledWhen(ContentTemplate::isEnabled)
                        .centerAligned()
                        .withFieldUpdater((rowIndex, contentTemplate, tickBoxState) -> {
                            if (enabledStateHandler != null) {
                                enabledStateHandler.accept(contentTemplate, tickBoxState.toBoolean());
                            }
                            dataGrid.redrawRow(rowIndex);
                        })
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether the template will be included for matching on data receipt.")
                        .centerAligned()
                        .build(),
                ColumnSizeConstants.ENABLED_COL);

        dataGrid.addColumn(
                DataGridUtil.columnBuilder(
                                DataGridUtil.toStringFunc(ContentTemplate::getTemplateNumber),
                                TextCell::new)
                        .enabledWhen(ContentTemplate::isEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Template No.")
                        .rightAligned()
                        .build(),
                100);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(ContentTemplate::getName)
                        .enabledWhen(ContentTemplate::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The name of the template.")
                        .build(),
                200);

//        dataGrid.addResizableColumn(
//                DataGridUtil.textColumnBuilder(ContentTemplate::getDescription)
//                        .enabledWhen(ContentTemplate::isEnabled)
//                        .build(),
//                DataGridUtil.headingBuilder("Description")
//                        .withToolTip("The description of the template.")
//                        .build(),
//                200);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(ContentTemplate::getTemplateType))
                        .enabledWhen(ContentTemplate::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Type")
                        .withToolTip("The type of content to create.")
                        .build(),
                140);

        dataGrid.addColumn(
                DataGridUtil.readOnlyTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(
                                ContentTemplate::isCopyElementDependencies))
                        .enabledWhen(ContentTemplate::isEnabled)
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("Copy Dependencies")
                        .withToolTip("If Template Type is INHERIT_PIPELINE then this option allows you to copy " +
                                     "any entities set within the properties of the pipeline being inherited from. " +
                                     "It will not copy entities referenced on any ancestor pipelines.")
                        .build(),
                140);

        final DocRefCell.Builder<ContentTemplate> docRefCellBuilder =
                new DocRefCell.Builder<ContentTemplate>()
                        .eventBus(getEventBus())
                        .docRefFunction(row ->
                                NullSafe.get(row, ContentTemplate::getPipeline))
                        .showIcon(true);
        dataGrid.addResizableColumn(
                DataGridUtil.docRefColumnBuilder(docRefCellBuilder)
                        .enabledWhen(ContentTemplate::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Pipeline")
                        .withToolTip("The pipeline to use as a template for creating content.")
                        .build(),
                300);

        dataGrid.addColumn(
                DataGridUtil.columnBuilder(
                                DataGridUtil.toStringFunc(ContentTemplate::getProcessorPriority),
                                TextCell::new)
                        .enabledWhen(ContentTemplate::isEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Priority")
                        .withToolTip("The priority that will be assigned to the pipeline processor on creation.")
                        .rightAligned()
                        .build(),
                100);

        dataGrid.addColumn(
                DataGridUtil.columnBuilder(
                                DataGridUtil.toStringFunc(ContentTemplate::getProcessorMaxConcurrent),
                                TextCell::new)
                        .enabledWhen(ContentTemplate::isEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Max Concurrent")
                        .withToolTip("The maximum number of concurrent tasks that will be assigned to the pipeline " +
                                     "processor on creation.")
                        .rightAligned()
                        .build(),
                130);

//        dataGrid.addAutoResizableColumn(
//                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(ContentTemplate::getExpression))
//                        .enabledWhen(ContentTemplate::isEnabled)
//                        .build(),
//                DataGridUtil.headingBuilder("Expression")
//                        .withToolTip("The expression to match received data with. " +
//                                     "Template expressions are tested in Template No. order, lowest first.")
//                        .build(),
//                100);

        dataGrid.addColumn(
                DataGridUtil.columnBuilder(
                                Function.identity(),
                                () -> new ActionCell<>(this::showActionMenu))
                        .enabledWhen(ContentTemplate::isEnabled)
                        .centerAligned()
                        .build(),
                "",
                24);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addColumn(final String name,
                           final int width,
                           final Function<ContentTemplate, String> valueFunc) {

        final Column<ContentTemplate, SafeHtml> expressionColumn = new Column<ContentTemplate, SafeHtml>(
                new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final ContentTemplate rule) {
                return getSafeHtml(valueFunc.apply(rule), rule);
            }
        };
        dataGrid.addResizableColumn(expressionColumn, name, width);
    }

    private void addActionButtonColumn(final String name,
                                       final int width) {

        final ActionCell<ContentTemplate> actionCell = new ActionCell<>(this::showActionMenu);

        final Column<ContentTemplate, ContentTemplate> expressionColumn =
                new Column<ContentTemplate, ContentTemplate>(actionCell) {

                    @Override
                    public ContentTemplate getValue(final ContentTemplate row) {
                        return row;
                    }

                    @Override
                    public void onBrowserEvent(final Context context,
                                               final Element elem,
                                               final ContentTemplate rule,
                                               final NativeEvent event) {
                        super.onBrowserEvent(context, elem, rule, event);
//                        GWT.log("Rule " + rule.getRuleNumber() + " clicked, event " + event.getType());
                    }
                };
        dataGrid.addResizableColumn(expressionColumn, name, width);
    }

    private void showActionMenu(final ContentTemplate row, final NativeEvent event) {
        final PopupPosition popupPosition = new PopupPosition(event.getClientX() + 10, event.getClientY());
        final List<Item> items = actionMenuItemProvider.apply(row);
        ShowMenuEvent
                .builder()
                .items(items)
                .popupPosition(popupPosition)
                .fire(this);
    }

//    private void addTickBoxColumn(final String name,
//                                  final int width,
//                                  final Function<ContentTemplate, Boolean> valueFunc) {
//
//        final Column<ContentTemplate, TickBoxState> enabledColumn = new Column<ContentTemplate, TickBoxState>(
//                TickBoxCell.create(false, false)) {
//
//            @Override
//            public TickBoxState getValue(final ContentTemplate rule) {
//                if (rule != null && !isDefaultRule(rule)) {
//                    return TickBoxState.fromBoolean(valueFunc.apply(rule));
//                }
//                return null;
//            }
//        };
//
//        enabledColumn.setFieldUpdater((index, rule, value) -> {
//            if (enabledStateHandler != null && !isDefaultRule(rule)) {
//                enabledStateHandler.accept(rule, value.toBoolean());
//            }
//        });
//        dataGrid.addColumn(enabledColumn, name, width);
//    }

//    private boolean isDefaultRule(final ContentTemplate rule) {
//        return Objects.equals(
//                DataRetentionPolicyPresenter.DEFAULT_UI_ONLY_RETAIN_ALL_RULE.getName(),
//                rule.getName());
//    }

    private SafeHtml getSafeHtml(final String string, final ContentTemplate rule) {
        if (!rule.isEnabled()) {
            return HtmlBuilder
                    .builder()
                    .span(hb ->
                            hb.append(string), Attribute.className("dataRetention--disabledRule"))
                    .toSafeHtml();
        } else {
            return SafeHtmlUtils.fromString(string);
        }
    }

    public void setData(final List<ContentTemplate> data) {
//        this.currentData = Objects.requireNonNullElseGet(data, ArrayList::new);
        dataGrid.setRowData(0, data);
        dataGrid.setRowCount(data.size());
//        refresh();
    }

//    private void refresh() {
//        dataGrid.setRowData(0, currentData);
//        dataGrid.setRowCount(currentData.size());
//    }

    public MultiSelectionModel<ContentTemplate> getSelectionModel() {
        return selectionModel;
    }

    public ButtonView add(final Preset preset) {
        return getView().addButton(preset);
    }

    public void setEnabledStateHandler(final BiConsumer<ContentTemplate, Boolean> enabledStateHandler) {
        this.enabledStateHandler = enabledStateHandler;
    }

    public void setActionMenuItemProvider(final Function<ContentTemplate, List<Item>> actionMenuItemProvider) {
        this.actionMenuItemProvider = actionMenuItemProvider;
    }
}
