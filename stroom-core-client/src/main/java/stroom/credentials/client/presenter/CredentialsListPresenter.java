package stroom.credentials.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.CredentialsDetailsDialogPresenter.CredentialsDetailsDialogView;
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
    private RestDataProvider<Credentials,  ResultPage<Credentials>> dataProvider;

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
        CredentialsViewImpl.console("CredentialsListPresenter ctor");

        this.dataGrid = new MyDataGrid<>(this);
        view.setDataWidget(dataGrid);
        this.gridSelectionModel = dataGrid.addDefaultSelectionModel(false);
        btnAdd = this.getView().addButton(SvgPresets.ADD);
        btnDelete = this.getView().addButton(SvgPresets.DELETE);
        btnEdit = this.getView().addButton(SvgPresets.EDIT);
    }

    private void init() {
        CredentialsViewImpl.console("init()");
        // REST to server


        // Create the grid

        dataGrid.setMultiLine(true);

        // Add buttons to the pager

        btnAdd.addClickHandler(event -> handleAddButtonClick());

        btnDelete.addClickHandler(event -> handleDeleteButtonClick());

        btnEdit.addClickHandler(event -> handleEditButtonClick());

        // Set selection model


        this.initColumns(dataGrid);

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
                    CredentialsViewImpl.console("Data loaded and first item selected");
                }
            }
        });

        // Set the state of the UI
        gridSelectionModel.addSelectionHandler(event -> updateState());

        // Hook up the data
        dataProvider = createDataProvider(super.getEventBus(),
                super.getView(),
                restFactory);
        dataProvider.addDataDisplay(dataGrid);

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
                        .method((r) -> {
                            CredentialsViewImpl.console("Getting creds from server");
                            return r.list(pageRequest);
                            //CredentialsViewImpl.console("Requested creds from server");
                        })
                        .onSuccess(dataConsumer)
                        .onFailure(restErrorHandler)
                        .taskMonitorFactory(view)
                        .exec();
            }
        };
    }

    /**
     * Called from CredentialsPresenter to hook UI elements together on initialisation.
     * Needed so that the spinner works when waiting for server.
     */
    public void setCredentialsPresenter(final CredentialsPresenter credentialsPresenter) {
        this.credentialsPresenter = credentialsPresenter;
        this.init();
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
        CredentialsViewImpl.console("updateState()");
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
        CredentialsViewImpl.console("handleAddButtonClick()");
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
        CredentialsViewImpl.console("handleEditButtonClick()");
        final Credentials credentials = gridSelectionModel.getSelected();
        showDetailsDialog(credentials);
    }

    /**
     * Called when adding or editing credentials.
     * @param credentials The credentials to add or edit.
     */
    private void showDetailsDialog(final Credentials credentials) {
        CredentialsViewImpl.console("showDetailsDialog()");
        if (credentials != null) {
            final ShowPopupEvent.Builder builder = ShowPopupEvent.builder(detailsDialog);
            detailsDialog.setupDialog(credentials, builder);
            builder.onHideRequest(e -> {
                        CredentialsViewImpl.console("showDetailsDialog() hide request");
                        if (e.isOk()) {
                            CredentialsViewImpl.console("showDetailsDialog() isOk");

                            if (detailsDialog.isValid()) {
                                CredentialsViewImpl.console("showDetailsDialog() isValid");
                                final CredentialsDetailsDialogView view = detailsDialog.getView();
                                CredentialsViewImpl.console("Got view: " + view);
                                final Credentials credsToSave = view.getCredentials();
                                CredentialsViewImpl.console("showDetailsDialog() creds to save " + credsToSave.toString());
                                e.hide();
                                CredentialsViewImpl.console("showDetailsDialog() hidden");

                                //saveCredentials(detailsDialog.getView().getCredentials());
                                saveCredentials(credsToSave);
                                CredentialsViewImpl.console("showDetailsDialog() creds saved");


                            } else {
                                CredentialsViewImpl.console("showDetailsDialog() not valid");

                                final String validationMessage = detailsDialog.getValidationMessage();
                                CredentialsViewImpl.console("showDetailsDialog() " + validationMessage);

                                if (validationMessage != null) {
                                    AlertEvent.fireWarn(detailsDialog,
                                            detailsDialog.getValidationMessage(),
                                            e::reset);
                                }
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
        CredentialsViewImpl.console("saveCredentials()");
        restFactory.create(CREDENTIALS_RESOURCE)
                .method(res -> {
                    CredentialsViewImpl.console("saveCredentials() res.store()");
                    return res.store(creds);
                })
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
                .onFailure(error -> {
                    CredentialsViewImpl.console("onFailure: " + error);
                })
                .taskMonitorFactory(CredentialsListPresenter.this)
                .exec();

    }

    /**
     * Refreshes the list from the DB.
     */
    void refreshList(final Credentials selected) {
        CredentialsViewImpl.console("refreshList()");
        dataProvider.refresh();
        if (selected != null) {
            // This doesn't work - don't know why.
            // Maybe the credentials objects don't match.
            gridSelectionModel.setSelected(selected);
        }
    }

}
