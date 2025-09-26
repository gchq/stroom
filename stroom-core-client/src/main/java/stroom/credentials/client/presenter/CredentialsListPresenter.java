package stroom.credentials.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.view.CredentialsViewImpl;
import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsResource;
import stroom.credentials.shared.CredentialsResponse.Status;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.LoadingStateChangeEvent.LoadingState;
import com.google.gwt.view.client.Range;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.google.inject.Inject;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shows the list of credentials.
 */
public class CredentialsListPresenter extends MyPresenterWidget<PagerView> {

    /** Reference to top level of page. Allows updating state */
    private CredentialsPresenter credentialsPresenter = null;

    /** Used to talk to the server */
    private final RestFactory restFactory;

    /** Formats the expiry date */
    private final DateTimeFormatter dateTimeFormatter;

    /** Where the data grid gets its data from */
    private final RestDataProvider<Credentials,  ResultPage<Credentials>> dataProvider;

    /** List of credentials */
    private final MyDataGrid<Credentials> dataGrid;

    /** What is selected in the list? */
    private final MultiSelectionModel<Credentials> gridSelectionModel;

    /** Button in view to add another credential */
    private final ButtonView btnAdd;

    /** Button in view to delete a credential */
    private final ButtonView btnDelete;

    /** Button in view to edit a credential */
    private final ButtonView btnEdit;

    /** Dialog to edit the credentials */
    private final CredentialsDetailsDialogPresenter detailsDialog;

    /** Index of the first item in the list of credentials */
    private static final int FIRST_ITEM_INDEX = 0;

    /** The resource to access server-side data */
    public static final CredentialsResource CREDENTIALS_RESOURCE
            = GWT.create(CredentialsResource.class);

    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    public CredentialsListPresenter(final EventBus eventBus,
                                    final PagerView view,
                                    final RestFactory restFactory,
                                    final CredentialsDetailsDialogPresenter detailsDialog,
                                    final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.detailsDialog = detailsDialog;
        this.dateTimeFormatter = dateTimeFormatter;

        // REST to server
        this.dataGrid = new MyDataGrid<>(this);

        // Create the grid
        view.setDataWidget(dataGrid);
        dataGrid.setMultiLine(true);

        // Add buttons to the pager
        btnAdd = this.getView().addButton(SvgPresets.ADD);
        btnAdd.addClickHandler(event -> handleAddButtonClick());
        btnDelete = this.getView().addButton(SvgPresets.DELETE);
        btnDelete.addClickHandler(event -> handleDeleteButtonClick());
        btnEdit = this.getView().addButton(SvgPresets.EDIT);
        btnEdit.addClickHandler(event -> handleEditButtonClick());

        // Set selection model
        this.gridSelectionModel = dataGrid.addDefaultSelectionModel(false);

        this.initColumns(dataGrid);

        // Hook up the data
        dataProvider = createDataProvider(eventBus,
                view,
                restFactory);
        dataProvider.addDataDisplay(dataGrid);

        // Define a class for the CSS
        this.getWidget().addStyleName("credentials-list");

        // Get notified when data has loaded and load up the first item
        dataGrid.addLoadingStateChangeHandler(event -> {
            if (event.getLoadingState() == LoadingState.LOADED
                || event.getLoadingState() == LoadingState.PARTIALLY_LOADED) {
                if (gridSelectionModel.getSelected() == null
                    && dataGrid.getRowCount() > 0) {
                    final Credentials credentials = dataGrid.getVisibleItem(FIRST_ITEM_INDEX);
                    gridSelectionModel.setSelected(credentials);
                }
            }
        });

        // Set the state of the UI
        gridSelectionModel.addSelectionHandler(event -> updateState());
        updateState();
    }

    /**
     * Initialise the columns in the data grid.
     */
    private void initColumns(final MyDataGrid<Credentials> grid) {
        grid.addResizableColumn(
                DataGridUtil.textColumnBuilder(Credentials::getName)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("Name of credentials")
                        .build(),
                300);

        grid.addResizableColumn(
                DataGridUtil.textColumnBuilder((Credentials c) -> dateTimeFormatter.format(c.getExpires()))
                        .build(),
                DataGridUtil.headingBuilder("Expires")
                        .withToolTip("When these credentials expire")
                        .build(),
                300);

        grid.addResizableColumn(
                DataGridUtil.textColumnBuilder((Credentials c) -> c.getType().getDisplayName())
                .build(),
        DataGridUtil.headingBuilder("Type")
                .withToolTip("Type of credential")
                .build(),
                300);

        grid.addEndColumn(new EndColumn<>());
    }

    /**
     * Sets up the data provider for the list of credentials.
     */
    private RestDataProvider<Credentials, ResultPage<Credentials>>
    createDataProvider(final EventBus eventBus,
                       final PagerView view,
                       final RestFactory restFactory) {
        CredentialsViewImpl.console("Creating data provider");
        return new RestDataProvider<>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<Credentials>> dataConsumer,
                                final RestErrorHandler restErrorHandler) {
                CredentialsViewImpl.console("Requesting range " + range);
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                restFactory
                        .create(CREDENTIALS_RESOURCE)
                        .method((r) -> r.list(pageRequest))
                        .onSuccess(dataConsumer)
                        .onFailure(restErrorHandler)
                        .taskMonitorFactory(view)
                        .exec();
            }
        };
    }

    /**
     * Called from CredentialsPresenter to hook UI elements together on initialisation.
     */
    public void setCredentialsPresenter(final CredentialsPresenter credentialsPresenter) {
        this.credentialsPresenter = credentialsPresenter;
    }

    /**
     * @return The selected items in the list. Called from the Details view.
     */
    //public MultiSelectionModel<Credentials> getSelectionModel() {
    //    return this.gridSelectionModel;
    //}

    /**
     * Updates the UI state.
     */
    private void updateState() {
        if (gridSelectionModel.getSelected() != null) {
            btnDelete.setEnabled(true);
            btnEdit.setEnabled(true);
        } else {
            btnDelete.setEnabled(false);
            btnEdit.setEnabled(false);
        }
    }

    /**
     * Called when the Add (+) button is clicked.
     */
    private void handleAddButtonClick() {
        final Credentials newCredentials = new Credentials();
        showDetailsDialog(newCredentials);
    }

    /**
     * Called when the delete button is clicked.
     */
    private void handleDeleteButtonClick() {
        CredentialsViewImpl.console("handleDeleteButtonClick");

        final Credentials selectedCredentials = gridSelectionModel.getSelected();
        if (selectedCredentials != null) {
            restFactory.create(CREDENTIALS_RESOURCE)
                    .method(res -> res.delete(selectedCredentials.getUuid()))
                    .onSuccess(result -> {
                        if (result.getStatus() == Status.OK) {
                            // Reload the list & select the first item
                            dataProvider.refresh();
                            final Credentials firstItem = dataGrid.getVisibleItem(FIRST_ITEM_INDEX);
                            if (firstItem != null) {
                                gridSelectionModel.setSelected(firstItem);
                            }
                        } else {
                            AlertEvent.fireError(credentialsPresenter,
                                    "Delete Error",
                                    result.getMessage(),
                                    null);
                        }
                    })
                    .taskMonitorFactory(CredentialsListPresenter.this)
                    .exec();
        }
    }

    /**
     * When edit button is clicked
     */
    private void handleEditButtonClick() {
        final Credentials credentials = gridSelectionModel.getSelected();
        showDetailsDialog(credentials);
    }

    /**
     * Called when adding or editing credentials.
     * @param credentials The credentials to add or edit.
     */
    private void showDetailsDialog(final Credentials credentials) {
        if (credentials != null) {
            final ShowPopupEvent.Builder builder = ShowPopupEvent.builder(detailsDialog);
            detailsDialog.setupDialog(credentials, builder);
            builder.onHideRequest(e -> {
                        if (e.isOk()) {
                            if (detailsDialog.isValid()) {
                                e.hide();
                                saveCredentials(detailsDialog.getView().getCredentials());

                            } else {
                                AlertEvent.fireWarn(detailsDialog,
                                        detailsDialog.getValidationMessage(),
                                        e::reset);
                            }
                        } else {
                            // Cancel pressed
                            e.hide();
                        }
                    })
                    .fire();
        }
    }

    private void saveCredentials(final Credentials creds) {
        restFactory.create(CREDENTIALS_RESOURCE)
                .method(res -> res.store(creds))
                .onSuccess(result -> {
                    CredentialsViewImpl.console("Saved credentials");
                    if (result.getStatus() == Status.OK) {
                        // Reload the list & select it
                        dataProvider.refresh();
                        // TODO This isn't working...
                        gridSelectionModel.setSelected(creds);

                    } else {
                        CredentialsViewImpl.console("Error saving credentials");

                        AlertEvent.fireError(credentialsPresenter,
                                "Save Error",
                                result.getMessage(),
                                null);
                    }
                })
                .taskMonitorFactory(CredentialsListPresenter.this)
                .exec();

    }

    /**
     * Refreshes the list from the DB.
     */
    void refreshList(final Credentials selected) {
        dataProvider.refresh();
        if (selected != null) {
            // This doesn't work - don't know why.
            // Maybe the credentials objects don't match.
            gridSelectionModel.setSelected(selected);
        }
    }

}
