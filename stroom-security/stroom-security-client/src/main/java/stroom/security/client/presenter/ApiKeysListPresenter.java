package stroom.security.client.presenter;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MyDataGrid;
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

import java.util.HashSet;
import java.util.Objects;
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
        final boolean enabled = !selection.isMatchNothing();
        editButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }


    private void createNewKey() {
        editApiKeyPresenter.show(Mode.CREATE, e -> {
            if (e.isOk()) {
                dataProvider.refresh();
//                selected = newPresenter.getUserName();
//                if (selected != null) {
                    HidePopupEvent.builder(this).fire();
//                }
            }
            e.hide();
        });
    }

    private void editSelectedKey() {
        editApiKeyPresenter.show(Mode.EDIT, e -> {
            if (e.isOk()) {
                dataProvider.refresh();
//                selected = newPresenter.getUserName();
//                if (selected != null) {
                HidePopupEvent.builder(this).fire();
//                }
            }
            e.hide();
        });
    }

    private void deleteSelectedKeys() {

    }

    private void initTableColumns() {

        final Column<ApiKey, TickBoxState> checkBoxColumn = DataGridUtil.columnBuilder(
                        (ApiKey row) ->
                                TickBoxState.fromBoolean(selection.isMatch(row.getId())),
                        () ->
                                TickBoxCell.create(false, false))
                .build();
        dataGrid.addColumn(checkBoxColumn, "", ColumnSizeConstants.CHECKBOX_COL);

        // Add Handlers
        checkBoxColumn.setFieldUpdater((index, row, value) -> {
            if (value.toBoolean()) {
                selection.add(row.getId());
            } else {
                selection.remove(row.getId());
            }
            dataGrid.redrawHeaders();
            DataSelectionEvent.fire(ApiKeysListPresenter.this, selection, false);
        });

        // Need manage users perm to CRUD keys for other users
        // If you don't have it no point in showing owner col
        if (securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            final Column<ApiKey, String> ownerColumn = DataGridUtil.textColumnBuilder(
                            (ApiKey row) ->
                                    row.getOwner().getUserIdentityForAudit())
                    .build();
            dataGrid.addResizableColumn(ownerColumn, "Owner", 250);
        }

        final Column<ApiKey, String> nameColumn = DataGridUtil.textColumnBuilder(ApiKey::getName)
                .build();
        dataGrid.addResizableColumn(nameColumn, "Name", 250);

        final Column<ApiKey, String> prefixColumn = DataGridUtil.textColumnBuilder(ApiKey::getApiKeyPrefix)
                .build();
        dataGrid.addColumn(prefixColumn, "Key Prefix", ColumnSizeConstants.MEDIUM_COL);

        final Column<ApiKey, String> enabledColumn = DataGridUtil.textColumnBuilder((ApiKey apiKey) ->
                        apiKey.getEnabled()
                                ? "Enabled"
                                : "Disabled")
                .build();
        dataGrid.addColumn(enabledColumn, "State", ColumnSizeConstants.SMALL_COL);

        final Column<ApiKey, String> expiresOnColumn = DataGridUtil.textColumnBuilder(
                        ApiKey::getExpireTimeMs, dateTimeFormatter::formatWithDuration)
                .build();
        dataGrid.addColumn(expiresOnColumn, "Expires On", ColumnSizeConstants.DATE_AND_DURATION_COL);

        final Column<ApiKey, String> commentsColumn = DataGridUtil.textColumnBuilder(ApiKey::getComments)
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
                    dataConsumer.accept(response);
//                    responseMap.put(nodeName, response.getValues());
//                    errorMap.put(nodeName, response.getErrors());
//                    delayedUpdate.update();
                    update();
                })
                .onFailure(throwable -> {
//                    responseMap.remove(nodeName);
//                    errorMap.put(nodeName, Collections.singletonList(throwable.getMessage()));
//                    delayedUpdate.update();
                })
                .call(API_KEY_RESOURCE)
                .find(criteria);
    }

    private void update() {

    }

    public void setQuickFilter(final String userInput) {

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
