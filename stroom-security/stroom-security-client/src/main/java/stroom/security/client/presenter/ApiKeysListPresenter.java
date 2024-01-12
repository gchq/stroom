package stroom.security.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.EditApiKeyPresenter.Mode;
import stroom.security.shared.ApiKey;
import stroom.security.shared.ApiKeyResource;
import stroom.security.shared.ApiKeyResultPage;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.PermissionNames;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.Selection;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class ApiKeysListPresenter
        extends MyPresenterWidget<PagerView>
        implements HasDataSelectionHandlers<Selection<Integer>> {

    private static final ApiKeyResource API_KEY_RESOURCE = GWT.create(ApiKeyResource.class);

    private final FindApiKeyCriteria criteria = new FindApiKeyCriteria();
    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private final ClientSecurityContext securityContext;
    private final EditApiKeyPresenter editApiKeyPresenter;

    //    private final Set<Integer> selectedApiKeyIds = new HashSet<>();
    private final Selection<Integer> selection = new Selection<>(false, new HashSet<>());
    private final MultiSelectionModelImpl<ApiKey> selectionModel;
    private final DataGridSelectionEventManager<ApiKey> selectionEventManager;
    private final QuickFilterTimer timer = new QuickFilterTimer();
    private final RestDataProvider<ApiKey, ApiKeyResultPage> dataProvider;
    private final MyDataGrid<ApiKey> dataGrid;
    private final InlineSvgButton addButton;
    private final InlineSvgButton editButton;
    private final InlineSvgButton deleteButton;

    private Range range;
    private Consumer<ApiKeyResultPage> dataConsumer;
    private Map<Integer, ApiKey> apiKeys = new HashMap<>();

    @Inject
    public ApiKeysListPresenter(final EventBus eventBus,
                                final PagerView view,
                                final RestFactory restFactory,
                                final DateTimeFormatter dateTimeFormatter,
                                final ClientSecurityContext securityContext,
                                final EditApiKeyPresenter editApiKeyPresenter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        this.securityContext = securityContext;
        this.editApiKeyPresenter = editApiKeyPresenter;
        this.dataGrid = new MyDataGrid<>(1000);
        this.selectionModel = new MultiSelectionModelImpl<>(dataGrid);
        this.selectionEventManager = new DataGridSelectionEventManager<>(dataGrid, selectionModel, false);
        this.dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        view.setDataWidget(dataGrid);

        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            // No Manage Users perms so can only see their own keys
            criteria.setOwner(securityContext.getUserName());
        }
        criteria.setSort(FindApiKeyCriteria.FIELD_NAME);

        addButton = new InlineSvgButton();
        editButton = new InlineSvgButton();
        deleteButton = new InlineSvgButton();
        initButtons();
        initTableColumns();
        dataProvider = new RestDataProvider<ApiKey, ApiKeyResultPage>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ApiKeyResultPage> dataConsumer,
                                final Consumer<Throwable> throwableConsumer) {
                ApiKeysListPresenter.this.range = range;
                ApiKeysListPresenter.this.dataConsumer = dataConsumer;
//                delayedUpdate.reset();
                fetchData(range, dataConsumer, throwableConsumer);
            }
        };
        dataProvider.addDataDisplay(dataGrid);
        selectionModel.addSelectionHandler(event -> {
            setButtonStates();
            if (event.getSelectionType().isDoubleSelect()) {
                editSelectedKey();
            }
        });
        dataGrid.addColumnSortHandler(event -> {
            if (event.getColumn() instanceof OrderByColumn<?, ?>) {
                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
                criteria.setSort(orderByColumn.getField(), !event.isSortAscending(), orderByColumn.isIgnoreCase());
                dataProvider.refresh();
            }
        });
    }

    private void initButtons() {
        addButton.setSvg(SvgImage.ADD);
        addButton.setTitle("Add new API key");
        addButton.addClickHandler(event -> createNewKey());
        getView().addButton(addButton);

        editButton.setSvg(SvgImage.EDIT);
        editButton.setTitle("Edit API key");
        editButton.addClickHandler(event -> editSelectedKey());
        getView().addButton(editButton);

        deleteButton.setSvg(SvgImage.DELETE);
        deleteButton.setTitle("Delete selected API key(s)");
        deleteButton.addClickHandler(event -> deleteSelectedKeys());
        getView().addButton(deleteButton);

        setButtonStates();
    }

    private void setButtonStates() {
        final boolean hasSelectedItems = GwtNullSafe.hasItems(selectionModel.getSelectedItems());
        editButton.setEnabled(hasSelectedItems);
        deleteButton.setEnabled(!selection.isMatchNothing());
    }

    private void createNewKey() {
        editApiKeyPresenter.showCreateDialog(Mode.PRE_CREATE, () -> {
            dataProvider.refresh();
//                selected = newPresenter.getUserName();
//                if (selected != null) {
            HidePopupEvent.builder(this).fire();
//                }
        });
    }

    private void editSelectedKey() {
        final ApiKey apiKey = selectionModel.getSelected();
        editApiKeyPresenter.showEditDialog(apiKey, Mode.EDIT, () -> {
            dataProvider.refresh();
//                selected = newPresenter.getUserName();
//                if (selected != null) {
            HidePopupEvent.builder(this).fire();
//                }
        });
    }

    private void deleteSelectedKeys() {
        // This is the one selected using row selection, not the checkbox
        final ApiKey selectedApiKey = selectionModel.getSelected();

        // The ones selected with the checkboxes
        final Set<Integer> selectedSet = GwtNullSafe.set(selection.getSet());
        final boolean clearSelection = selectedApiKey != null && selectedSet.contains(selectedApiKey.getId());
        final List<Integer> selectedItems = new ArrayList<>(selectedSet);

        final Runnable onSuccess = () -> {
            if (clearSelection) {
                selectionModel.clear();
            }
            selection.clear();
            refresh();
        };

        final Consumer<Throwable> onFailure = throwable -> {
            // Something went wrong so refresh the data.
            AlertEvent.fireError(this, throwable.getMessage(), null);
            refresh();
        };

        final int cnt = selectedItems.size();
        if (cnt == 1) {
            final int id = selectedItems.get(0);
            final ApiKey apiKey = apiKeys.get(id);
            final String msg = "Are you sure you want to delete API Key '"
                    + apiKey.getName()
                    + "' with prefix '"
                    + apiKey.getApiKeyPrefix()
                    + "'? Once deleted, anyone using this API Key will no longer by able to authenticate with it.";
            ConfirmEvent.fire(this, msg, ok -> {
//                GWT.log("id: " + id);
                if (ok) {
                    final Rest<Boolean> rest = restFactory.create();
                    rest
                            .onSuccess(unused -> {
                                onSuccess.run();
                            })
                            .onFailure(onFailure)
                            .call(API_KEY_RESOURCE)
                            .delete(id);
                }
            });
        } else if (cnt > 1) {
            final String msg = "Are you sure you want to delete " + selectedItems.size() + " API keys? " +
                    "Once deleted, anyone using these API Keys will no longer by able to authenticate with them.";
            ConfirmEvent.fire(this, msg, ok -> {
                if (ok) {
                    final Rest<Integer> rest = restFactory.create();
                    rest
                            .onSuccess(count -> {
                                onSuccess.run();
                            })
                            .onFailure(onFailure)
                            .call(API_KEY_RESOURCE)
                            .deleteBatch(selection.getSet());
                }
            });
        }
    }

    private void initTableColumns() {

        final Column<ApiKey, TickBoxState> checkBoxColumn = DataGridUtil.columnBuilder(
                        (ApiKey row) ->
                                TickBoxState.fromBoolean(selection.isMatch(row.getId())),
                        () -> TickBoxCell.create(false, false))
                .build();
        dataGrid.addColumn(checkBoxColumn, "", ColumnSizeConstants.CHECKBOX_COL);

        // Add Handlers
        checkBoxColumn.setFieldUpdater((index, apiKey, value) -> {
            if (value.toBoolean()) {
                selection.add(apiKey.getId());
            } else {
                selection.remove(apiKey.getId());
            }
            setButtonStates();
            dataGrid.redrawHeaders();
            DataSelectionEvent.fire(ApiKeysListPresenter.this, selection, false);
        });

        // Need manage users perm to CRUD keys for other users
        // If you don't have it no point in showing owner col
        if (securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            final Column<ApiKey, String> ownerColumn = DataGridUtil.textColumnBuilder(
                            (ApiKey row) ->
                                    row.getOwner().getUserIdentityForAudit())
                    .enabledWhen(ApiKey::getEnabled)
                    .withSorting(FindApiKeyCriteria.FIELD_OWNER)
                    .build();
            dataGrid.addResizableColumn(ownerColumn, "Owner", 250);
        }

        final Column<ApiKey, String> nameColumn = DataGridUtil.textColumnBuilder(ApiKey::getName)
                .enabledWhen(ApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_NAME)
                .build();
        dataGrid.addResizableColumn(nameColumn, "Name", 250);

        final Column<ApiKey, String> prefixColumn = DataGridUtil.textColumnBuilder(ApiKey::getApiKeyPrefix)
                .enabledWhen(ApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_PREFIX)
                .build();
        dataGrid.addColumn(prefixColumn, "Key Prefix", 130);

        final Column<ApiKey, String> enabledColumn = DataGridUtil.textColumnBuilder((ApiKey apiKey) ->
                        apiKey.getEnabled()
                                ? "Enabled"
                                : "Disabled")
                .enabledWhen(ApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_STATE)
                .build();
        dataGrid.addColumn(enabledColumn, "State", ColumnSizeConstants.SMALL_COL);

        final Column<ApiKey, String> expiresOnColumn = DataGridUtil.textColumnBuilder(
                        ApiKey::getExpireTimeMs, dateTimeFormatter::formatWithDuration)
                .enabledWhen(ApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_EXPIRE_TIME)
                .build();
        dataGrid.addColumn(expiresOnColumn, "Expires On", ColumnSizeConstants.DATE_AND_DURATION_COL);

        final Column<ApiKey, String> commentsColumn = DataGridUtil.textColumnBuilder(ApiKey::getComments)
                .enabledWhen(ApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_COMMENTS)
                .build();
        dataGrid.addAutoResizableColumn(commentsColumn, "Comments", ColumnSizeConstants.BIG_COL);

        DataGridUtil.addEndColumn(dataGrid);
    }

    private void fetchData(final Range range,
                           final Consumer<ApiKeyResultPage> dataConsumer,
                           final Consumer<Throwable> throwableConsumer) {

        final Rest<ApiKeyResultPage> rest = restFactory.create();
        rest
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
                .call(API_KEY_RESOURCE)
                .find(criteria);
    }

    public void setQuickFilter(final String userInput) {
        timer.setName(userInput);
        timer.cancel();
        timer.schedule(400);
    }

    public void refresh() {
        internalRefresh();
    }

    private void internalRefresh() {
        dataProvider.refresh();
        setButtonStates();
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<Selection<Integer>> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }


    // --------------------------------------------------------------------------------


    private class QuickFilterTimer extends Timer {

        private String name;

        @Override
        public void run() {
            String filter = name;
            if (filter != null) {
                filter = filter.trim();
                if (filter.length() == 0) {
                    filter = null;
                }
            }

            if (!Objects.equals(filter, criteria.getQuickFilterInput())) {

                // This is a new filter so reset all the expander states
                criteria.setQuickFilterInput(filter);
                internalRefresh();
            }
        }

        public void setName(final String name) {
            this.name = name;
        }
    }


    // --------------------------------------------------------------------------------


    @Deprecated
    public interface ApiKeysListView extends View {

        void setTable(Widget widget);
    }
}
