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

package stroom.security.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.cell.info.client.ActionMenuCell;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.ExpressionOperator;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.ApiKeyResource;
import stroom.security.shared.AppPermission;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.HashedApiKey;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.UserFields;
import stroom.svg.client.Preset;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class ApiKeysListPresenter
        extends MyPresenterWidget<QuickFilterPageView>
        implements QuickFilterUiHandlers {

    private static final ApiKeyResource API_KEY_RESOURCE = GWT.create(ApiKeyResource.class);


    private final FindApiKeyCriteria.Builder criteriaBuilder = new FindApiKeyCriteria.Builder();
    //    private final FindApiKeyCriteria criteria = new FindApiKeyCriteria();
    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private final ClientSecurityContext securityContext;
    private final EditApiKeyPresenter editApiKeyPresenter;
    private final PagerView pagerView;

    //    private final Set<Integer> selectedApiKeyIds = new HashSet<>();
    private final Selection<Integer> selection = new Selection<>(false, new HashSet<>());
    private final MultiSelectionModelImpl<HashedApiKey> selectionModel;
    //    private final DataGridSelectionEventManager<HashedApiKey> selectionEventManager;
//    private final QuickFilterTimer timer = new QuickFilterTimer();
    private final MyDataGrid<HashedApiKey> dataGrid;
    private final UiConfigCache uiConfigCache;
//    private final InlineSvgButton addButton;
//    private final InlineSvgButton editButton;
//    private final InlineSvgButton deleteButton;

    private RestDataProvider<HashedApiKey, ResultPage<HashedApiKey>> dataProvider = null;
    private Range range;
    private Consumer<ResultPage<HashedApiKey>> dataConsumer;
    private Map<Integer, HashedApiKey> apiKeys = new HashMap<>();
    private boolean isExternalIdp = false;
    private String filter;
    private UserRef owner = null;

    @Inject
    public ApiKeysListPresenter(final EventBus eventBus,
                                final PagerView pagerView,
                                final QuickFilterPageView listView,
                                final RestFactory restFactory,
                                final DateTimeFormatter dateTimeFormatter,
                                final ClientSecurityContext securityContext,
                                final EditApiKeyPresenter editApiKeyPresenter,
                                final UiConfigCache uiConfigCache) {
        super(eventBus, listView);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        this.securityContext = securityContext;
        this.editApiKeyPresenter = editApiKeyPresenter;
        this.uiConfigCache = uiConfigCache;
        this.pagerView = pagerView;
        this.dataGrid = new MyDataGrid<>(this);
        this.selectionModel = dataGrid.addDefaultSelectionModel(true);
//        this.selectionEventManager = new DataGridSelectionEventManager<>(
//                dataGrid, selectionModel, false);
//        this.dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        pagerView.setDataWidget(dataGrid);

        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            // No Manage Users perms so can only see their own keys
            criteriaBuilder.owner(securityContext.getUserRef());
        }

        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                listView.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "API Keys Quick Filter",
                        FindApiKeyCriteria.FILTER_FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);

        listView.setDataView(pagerView);
        listView.setUiHandlers(this);

//        addButton = new InlineSvgButton();
//        editButton = new InlineSvgButton();
//        deleteButton = new InlineSvgButton();
//        initButtons();
//        initTableColumns();
//        selectionModel.addSelectionHandler(event -> {
//            setButtonStates();
//            if (event.getSelectionType().isDoubleSelect()) {
//                editSelectedKey();
//            }
//        });
//        dataGrid.addColumnSortHandler(event -> {
//            if (event.getColumn() instanceof OrderByColumn<?, ?>) {
//                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
//                criteria.setSort(orderByColumn.getField(), !event.isSortAscending(), orderByColumn.isIgnoreCase());
//                dataProvider.refresh();
//            }
//        });
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    public MultiSelectionModelImpl<HashedApiKey> getSelectionModel() {
        return selectionModel;
    }

//    private void initButtons() {
//        addButton.setSvg(SvgImage.ADD);
//        addButton.setTitle("Add new API key");
//        addButton.addClickHandler(event -> createNewKey());
//        getView().addButton(addButton);
//
//        editButton.setSvg(SvgImage.EDIT);
//        editButton.setTitle("Edit API key");
//        editButton.addClickHandler(event -> editSelectedKey());
//        getView().addButton(editButton);
//
//        deleteButton.setSvg(SvgImage.DELETE);
//        deleteButton.setTitle("Delete selected API key(s)");
//        deleteButton.addClickHandler(event -> deleteSelectedKeys());
//        getView().addButton(deleteButton);
//
//        setButtonStates();
//    }

//    private void setButtonStates() {
//        final boolean hasSelectedItems = NullSafe.hasItems(selectionModel.getSelectedItems());
//        editButton.setEnabled(hasSelectedItems);
//        deleteButton.setEnabled(!selection.isMatchNothing());
//    }

//    private void createNewKey() {
//        editApiKeyPresenter.showCreateDialog(Mode.PRE_CREATE, dataProvider::refresh);
//    }
//
//    private void editSelectedKey() {
//        final HashedApiKey apiKey = selectionModel.getSelected();
//        editApiKeyPresenter.showEditDialog(apiKey, Mode.EDIT, dataProvider::refresh);
//    }
//
//    private void deleteSelectedKeys() {
//        // This is the one selected using row selection, not the checkbox
//        final HashedApiKey selectedApiKey = selectionModel.getSelected();
//
//        // The ones selected with the checkboxes
//        final Set<Integer> selectedSet = NullSafe.set(selection.getSet());
//        final boolean clearSelection = selectedApiKey != null && selectedSet.contains(selectedApiKey.getId());
//        final List<Integer> selectedItems = new ArrayList<>(selectedSet);
//
//        final Runnable onSuccess = () -> {
//            if (clearSelection) {
//                selectionModel.clear();
//            }
//            selection.clear();
//            refresh();
//        };
//
//        final RestErrorHandler onFailure = restError -> {
//            // Something went wrong so refresh the data.
//            AlertEvent.fireError(this, restError.getMessage(), null);
//            refresh();
//        };
//
//        final int cnt = selectedItems.size();
//        if (cnt == 1) {
//            final int id = selectedItems.get(0);
//            final HashedApiKey apiKey = apiKeys.get(id);
//            final String msg = "Are you sure you want to delete API Key '"
//                               + apiKey.getName()
//                               + "' with prefix '"
//                               + apiKey.getApiKeyPrefix()
//                               + "'?" +
//                               "\n\nOnce deleted, anyone using this API Key will no longer by able
//                               to authenticate with it "
//                               + "and it will not be possible to re-create it.";
//            ConfirmEvent.fire(this, msg, ok -> {
////                GWT.log("id: " + id);
//                if (ok) {
//                    restFactory
//                            .create(API_KEY_RESOURCE)
//                            .method(res -> res.delete(id))
//                            .onSuccess(unused -> {
//                                onSuccess.run();
//                            })
//                            .onFailure(onFailure)
//                            .taskMonitorFactory(this)
//                            .exec();
//                }
//            });
//        } else if (cnt > 1) {
//            final String msg = "Are you sure you want to delete " + selectedItems.size() + " API keys?" +
//                               "\n\nOnce deleted, anyone using these API Keys will no longer by able to
//                               authenticate with them " +
//                               "and it will not be possible to re-create them.";
//            ConfirmEvent.fire(this, msg, ok -> {
//                if (ok) {
//                    restFactory
//                            .create(API_KEY_RESOURCE)
//                            .method(res -> res.deleteBatch(selection.getSet()))
//                            .onSuccess(count -> {
//                                onSuccess.run();
//                            })
//                            .onFailure(onFailure)
//                            .taskMonitorFactory(this)
//                            .exec();
//                }
//            });
//        }
//    }

    private void initTableColumns() {
//        final Column<HashedApiKey, TickBoxState> checkBoxColumn = DataGridUtil.columnBuilder(
//                        (HashedApiKey row) ->
//                                TickBoxState.fromBoolean(selection.isMatch(row.getId())),
//                        () -> TickBoxCell.create(false, false))
//                .build();
//        dataGrid.addColumn(checkBoxColumn, "", ColumnSizeConstants.CHECKBOX_COL);

        // Add Handlers
//        checkBoxColumn.setFieldUpdater((index, apiKey, value) -> {
//            if (value.toBoolean()) {
//                selection.add(apiKey.getId());
//            } else {
//                selection.remove(apiKey.getId());
//            }
////            setButtonStates();
//            dataGrid.redrawHeaders();
//            DataSelectionEvent.fire(ApiKeysListPresenter.this, selection, false);
//        });

        // Need manage users perm to CRUD keys for other users
        // If you don't have it no point in showing owner col
        // Also don't show it if this screen has been set with a single owner
        if (securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
            && owner == null) {
            dataGrid.addColumn(
                    DataGridUtil.svgPresetColumnBuilder(false, (HashedApiKey row) ->
                                    UserAndGroupHelper.mapUserRefTypeToIcon(row.getOwner()))
                            .withSorting(UserFields.FIELD_IS_GROUP)
                            .centerAligned()
                            .build(),
                    DataGridUtil.headingBuilder("")
                            .headingText(UserAndGroupHelper.buildUserAndGroupIconHeader())
                            .centerAligned()
                            .withToolTip("Whether this key is for a single user or a named user group.")
                            .build(),
                    (ColumnSizeConstants.ICON_COL * 2) + 20);

            final Column<HashedApiKey, String> ownerColumn = DataGridUtil.textColumnBuilder(
                            (HashedApiKey row) ->
                                    row.getOwner().toDisplayString())
                    .enabledWhen(HashedApiKey::getEnabled)
                    .withSorting(FindApiKeyCriteria.FIELD_OWNER)
                    .build();
            dataGrid.addResizableColumn(ownerColumn, "Owner", 250);
        }

        // Key Name
        final Column<HashedApiKey, String> nameColumn = DataGridUtil.textColumnBuilder(HashedApiKey::getName)
                .enabledWhen(HashedApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_NAME)
                .build();
        dataGrid.addResizableColumn(nameColumn, "Key Name", 250);

        // Key Prefix
        final Column<HashedApiKey, String> prefixColumn = DataGridUtil.textColumnBuilder(HashedApiKey::getApiKeyPrefix)
                .enabledWhen(HashedApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_PREFIX)
                .build();
        dataGrid.addColumn(prefixColumn, "Key Prefix", 130);

        // Enabled state
        final Column<HashedApiKey, String> enabledColumn = DataGridUtil.textColumnBuilder((HashedApiKey apiKey) ->
                        apiKey.getEnabled()
                                ? "Enabled"
                                : "Disabled")
                .enabledWhen(HashedApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_STATE)
                .build();
        dataGrid.addColumn(enabledColumn, "State", ColumnSizeConstants.SMALL_COL);

        // Expires on
        final Column<HashedApiKey, String> expiresOnColumn = DataGridUtil.textColumnBuilder(
                        (HashedApiKey key) -> dateTimeFormatter.formatWithDuration(key.getExpireTimeMs()))
                .enabledWhen(HashedApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_EXPIRE_TIME)
                .build();
        dataGrid.addColumn(expiresOnColumn, "Expires On", ColumnSizeConstants.DATE_AND_DURATION_COL);
        dataGrid.sort(expiresOnColumn);

        // Hash algorithm
        final Column<HashedApiKey, String> hashAlgorithmColumn = DataGridUtil.textColumnBuilder(
                        (HashedApiKey key) -> key.getHashAlgorithm().getDisplayValue())
                .enabledWhen(HashedApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_HASH_ALGORITHM)
                .build();
        dataGrid.addResizableColumn(hashAlgorithmColumn, "Hash Algorithm", 120);

        // Comments
        final Column<HashedApiKey, String> commentsColumn = DataGridUtil.textColumnBuilder(HashedApiKey::getComments)
                .enabledWhen(HashedApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_COMMENTS)
                .build();
        dataGrid.addAutoResizableColumn(commentsColumn, "Comments", ColumnSizeConstants.BIG_COL);

        // Actions Menu btn
        final Column<HashedApiKey, HashedApiKey> actionMenuCol = DataGridUtil.columnBuilder(
                        Function.identity(),
                        () -> new ActionMenuCell<>(
                                (HashedApiKey hashedApiKey) -> UserAndGroupHelper.buildUserActionMenu(
                                        NullSafe.get(hashedApiKey, HashedApiKey::getOwner),
                                        isExternalIdp(),
                                        getActionScreensToInclude(),
                                        this,
                                        null),
                                this))
//                .enabledWhen(User::isEnabled)
                .build();

        // x2 width so when it is hard right, it doesn't get in the way of the scroll bar
        dataGrid.addColumn(
                actionMenuCol,
                "",
                ColumnSizeConstants.ICON_COL + 10);

        DataGridUtil.addEndColumn(dataGrid);
    }

    private Set<UserScreen> getActionScreensToInclude() {
        // If single owner has been set, it means this screen is embedded in a user-centric one
        // so allow jumping to the top level API keys screen
        return owner != null
                ? UserScreen.all()
                : UserScreen.allExcept(UserScreen.API_KEYS);
    }

    private void fetchData(final Range range,
                           final Consumer<ResultPage<HashedApiKey>> dataConsumer,
                           final RestErrorHandler errorHandler,
                           final TaskMonitorFactory taskMonitorFactory) {
        final ExpressionOperator expression = QuickFilterExpressionParser
                .parse(filter, FindApiKeyCriteria.DEFAULT_FIELDS, FindApiKeyCriteria.ALL_FIELDs_MAP);

        criteriaBuilder.expression(expression);
        criteriaBuilder.pageRequest(CriteriaUtil.createPageRequest(range));
        criteriaBuilder.sortList(CriteriaUtil.createSortList(dataGrid.getColumnSortList()));

        restFactory
                .create(API_KEY_RESOURCE)
                .method(res -> res.find(criteriaBuilder.build()))
                .onSuccess(response -> {
                    apiKeys.clear();
                    response.stream()
                            .forEach(apiKey -> apiKeys.put(apiKey.getId(), apiKey));

                    // Make sure we don't have any selected IDs that are not in the visible data.
                    final Set<Integer> selectionSet = selection.getSet();
                    final Set<Integer> idsToRemove = new HashSet<>();
                    selectionSet.forEach(id -> {
                        if (!apiKeys.containsKey(id)) {
                            idsToRemove.add(id);
                        }
                    });
                    selection.removeAll(idsToRemove);

                    dataConsumer.accept(response);
                })
                .onFailure(throwable -> {
                    AlertEvent.fireError(
                            this,
                            "Error fetching API Keys: " + throwable.getMessage(),
                            null);
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void setQuickFilter(final String userInput) {
        clear();
        getView().setQuickFilterText(userInput);
        onFilterChange(userInput);
    }

    /**
     * Sets the quickFilter to filter on the passed user.
     * Use this when jumping to the screen from one of the other security screens.
     */
    public void showUser(final UserRef owner) {
        if (owner != null) {
            setQuickFilter(FindApiKeyCriteria.FIELD_DEF_OWNER_DISPLAY_NAME.getFilterQualifier()
                           + ":" + owner.getDisplayName());
        }
    }

    /**
     * Set the user so the screen can only show one user. No quick filter is set.
     * Only use on an embedded Api keys list for ONE user.
     */
    public void setOwner(final UserRef owner) {
        clear();
        this.owner = owner;
        criteriaBuilder.owner(owner);
        refresh();
    }

    public void clear() {
//        GWT.log(name + " - clear");
        selectionModel.clear();
        if (dataProvider != null) {
            dataProvider.getDataDisplays().forEach(hasData -> {
                hasData.setRowData(0, Collections.emptyList());
                hasData.setRowCount(0, true);
            });
        }
    }

    public void refresh() {
        if (dataProvider == null) {
            uiConfigCache.get(extendedUiConfig -> {
                isExternalIdp = extendedUiConfig.isExternalIdentityProvider();
                initDataProvider();
            });
        } else {
//            GWT.log(name + " - refresh");
            internalRefresh();
        }
    }

    private void initDataProvider() {
//        GWT.log(name + " - initDataProvider");
        initTableColumns();
        //noinspection Convert2Diamond // Cos GWT
        dataProvider = new RestDataProvider<HashedApiKey, ResultPage<HashedApiKey>>(getEventBus()) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<HashedApiKey>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                ApiKeysListPresenter.this.range = range;
                ApiKeysListPresenter.this.dataConsumer = dataConsumer;
                fetchData(range, dataConsumer, errorHandler, pagerView);
            }

            @Override
            protected void changeData(final ResultPage<HashedApiKey> data) {
                super.changeData(data);
                if (!data.isEmpty()) {
                    selectionModel.setSelected(data.getFirst());
                } else {
                    selectionModel.clear();
                }
            }
        };
        dataProvider.addDataDisplay(dataGrid);
    }

    private void internalRefresh() {
        dataProvider.refresh();
    }

//    @Override
//    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<Selection<Integer>> handler) {
//        return addHandlerToSource(DataSelectionEvent.getType(), handler);
//    }

    private boolean isExternalIdp() {
        return isExternalIdp;
    }

    public ButtonView addButton(final Preset preset) {
        return pagerView.addButton(preset);
    }

    @Override
    public void onFilterChange(final String text) {
        filter = NullSafe.trim(text);
        if (filter.isEmpty()) {
            filter = null;
        }
        refresh();
    }
}
