package stroom.credentials.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.credentials.client.presenter.CredentialsSettingsPresenter.CredentialsSettingsView;
import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsCreateRequest;
import stroom.credentials.shared.CredentialsResource;
import stroom.credentials.shared.CredentialsResponse.Status;
import stroom.credentials.shared.CredentialsSecret;
import stroom.credentials.shared.CredentialsWithPerms;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent.Builder;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.LoadingStateChangeEvent.LoadingState;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

/**
 * Shows the list of credentials.
 */
public class CredentialsListPresenter extends MyPresenterWidget<PagerView> {

    /** Reference to top level of page. Allows updating state */
    private MyPresenterWidget<?> parentPresenter = null;

    /** Used to talk to the server */
    private final RestFactory restFactory;

    /** Formats the expiry date */
    private final DateTimeFormatter dateTimeFormatter;

    /** Where the data grid gets its data from */
    private RestDataProvider<CredentialsWithPerms,  ResultPage<CredentialsWithPerms>> dataProvider;

    /** List of credentials */
    private final MyDataGrid<CredentialsWithPerms> dataGrid;

    /** What is selected in the list? */
    private final MultiSelectionModel<CredentialsWithPerms> gridSelectionModel;

    /** Button in view to add another credential */
    private final ButtonView btnAdd;

    /** Button in view to delete a credential */
    private final ButtonView btnDelete;

    /** Button in view to edit a credential */
    private final ButtonView btnEdit;

    /** Dialog to edit the credentials */
    private final CredentialsDetailsTabDialogPresenter detailsDialog;

    /** Permissions client */
    private final ClientSecurityContext securityContext;

    /** Flag to set whether something is selected by default */
    private boolean defaultSelection = true;

    /** Index of the first item in the list of credentials */
    private static final int FIRST_ITEM_INDEX = 0;

    /** The resource to access server-side data */
    public static final CredentialsResource CREDENTIALS_RESOURCE
            = GWT.create(CredentialsResource.class);

    /** ID of the presenter for the list of credentials */
    public static final String CREDENTIALS_LIST = "CREDENTIALS_LIST";

    /** Indicates whether the credentials are new ones to be created or old ones to be stored */
    enum CreationState {
        NEW_CREDENTIALS,
        OLD_CREDENTIALS
    }

    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    public CredentialsListPresenter(final EventBus eventBus,
                                    final PagerView view,
                                    final RestFactory restFactory,
                                    final CredentialsDetailsTabDialogPresenter detailsDialog,
                                    final DateTimeFormatter dateTimeFormatter,
                                    final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.detailsDialog = detailsDialog;
        this.dateTimeFormatter = dateTimeFormatter;
        this.securityContext = securityContext;

        this.dataGrid = new MyDataGrid<>(this);
        view.setDataWidget(dataGrid);
        dataGrid.setMultiLine(true);
        dataGrid.setWidth("100%");
        this.gridSelectionModel = dataGrid.addDefaultSelectionModel(false);

        btnAdd = this.getView().addButton(SvgPresets.ADD);
        btnDelete = this.getView().addButton(SvgPresets.DELETE);
        btnEdit = this.getView().addButton(SvgPresets.EDIT);

        this.initColumns(dataGrid);

        // Define a class for the CSS
        this.getWidget().addStyleName("credentials-list");
    }

    /**
     * Initialise the columns in the data grid.
     */
    private void initColumns(final MyDataGrid<CredentialsWithPerms> grid) {
        grid.addResizableColumn(
                DataGridUtil.textColumnBuilder(this::getCredentialsName)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("Name of credentials")
                        .build(),
                280);

        grid.addResizableColumn(
                DataGridUtil.textColumnBuilder(this::getCredentialsExpires)
                        .build(),
                DataGridUtil.headingBuilder("Expires")
                        .withToolTip("When these credentials expire")
                        .build(),
                190);

        grid.addResizableColumn(
                DataGridUtil.textColumnBuilder(this::getCredentialsType)
                        .build(),
                DataGridUtil.headingBuilder("Type")
                        .withToolTip("Type of credential")
                        .build(),
                150);

        grid.addEndColumn(new EndColumn<>());
    }

    /**
     * Provides the name of the credentials to the data grid column.
     */
    private String getCredentialsName(final CredentialsWithPerms cwp) {
        return cwp.getCredentials().getName();
    }

    /**
     * Provides the expiry date of the credentials to the data grid column.
     */
    private String getCredentialsExpires(final CredentialsWithPerms cwp) {
        if (cwp.getCredentials().isCredsExpire()) {
            return dateTimeFormatter.format(cwp.getCredentials().getExpires());
        } else {
            return "";
        }
    }

    /**
     * Provides the type of the credentials to the data grid column.
     */
    private String getCredentialsType(final CredentialsWithPerms cwp) {
        return cwp.getCredentials().getType().getDisplayName();
    }

    /**
     * Sets up the data provider for the list of credentials.
     */
    private RestDataProvider<CredentialsWithPerms, ResultPage<CredentialsWithPerms>>
        createDataProvider(final EventBus eventBus,
                           final PagerView view,
                           final RestFactory restFactory) {
        return new RestDataProvider<>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<CredentialsWithPerms>> dataConsumer,
                                final RestErrorHandler restErrorHandler) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                restFactory
                        .create(CREDENTIALS_RESOURCE)
                        .method(r -> r.listCredentials(pageRequest))
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
    public void setParentPresenter(final MyPresenterWidget<?> parentPresenter) {
        this.parentPresenter = parentPresenter;
    }

    /**
     * Called when the presenter gets loaded into the UI. Sets up the event handlers.
     */
    @Override
    protected void onBind() {
        super.onBind();

        // Avoid permissions issues if the user doesn't have the AppPermission to see the data
        if (securityContext.hasAppPermission(AppPermission.CREDENTIALS)) {
            dataProvider = createDataProvider(super.getEventBus(),
                    super.getView(),
                    restFactory);
            dataProvider.addDataDisplay(dataGrid);
        }

        btnAdd.addClickHandler(event -> handleAddButtonClick());
        btnDelete.addClickHandler(event -> handleDeleteButtonClick());
        btnEdit.addClickHandler(event -> handleEditButtonClick());

        registerHandler(gridSelectionModel.addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                // Edit the credentials
                handleEditButtonClick();
            }
        }));

        // Set the state of the UI
        gridSelectionModel.addSelectionHandler(event -> updateState());

        // Get notified when data has loaded and load up the first item
        dataGrid.addLoadingStateChangeHandler(event -> {
            if (event.getLoadingState() == LoadingState.LOADED
                || event.getLoadingState() == LoadingState.PARTIALLY_LOADED) {
                if (defaultSelection) {
                    if (gridSelectionModel.getSelected() == null
                        && dataGrid.getRowCount() > 0) {
                        final CredentialsWithPerms cwp = dataGrid.getVisibleItem(FIRST_ITEM_INDEX);
                        gridSelectionModel.setSelected(cwp);
                    }
                }
            }
        });

        updateState();
    }

    /**
     * Allows clients to turn off the selection of the first item in the list if nothing
     * else is selected.
     * If this isn't called then the default selection will be made.
     * @param defaultSelection true if the first item should be selected if nothing
     *                         else is selected. false if nothing should be selected
     *                         in that case.
     */
    public void setDefaultSelection(final boolean defaultSelection) {
        this.defaultSelection = defaultSelection;
    }

    public void setSelectedCredentialsId(final String uuid) {
        dataProvider.refresh();
        if (uuid != null) {
            restFactory.create(CREDENTIALS_RESOURCE)
                    .method(res -> res.getCredentials(uuid))
                    .onSuccess(res -> {
                        if (res.getStatus() == Status.OK) {
                            final CredentialsWithPerms cwp = res.getCredentialsWithPerms();
                            setSelectedCredentials(cwp);
                        } else {
                            AlertEvent.fireError(parentPresenter,
                                    "Error finding selected credentials",
                                    res.getMessage(),
                                    null);
                        }
                    })
                    .onFailure(error -> AlertEvent.fireError(parentPresenter,
                            "Error finding selected credentials",
                            error.getMessage(),
                            null))
                    .taskMonitorFactory(CredentialsListPresenter.this)
                    .exec();
        } else {
            setSelectedCredentials(null);
        }
    }

    /**
     * Sets the selected credentials in the UI.
     * @param cwp The credentials to show as selected. Can be null if nothing is selected.
     */
    private void setSelectedCredentials(final CredentialsWithPerms cwp) {
        gridSelectionModel.setSelected(cwp);
    }

    /**
     * @return The ID of the currently selected credentials, or null if nothing is selected.
     */
    public String getSelectedCredentialsId() {
        final CredentialsWithPerms selectedCwp = gridSelectionModel.getSelected();
        if (selectedCwp != null) {
            return selectedCwp.getCredentials().getUuid();
        } else {
            return null;
        }
    }

    /**
     * Updates the UI state.
     */
    private void updateState() {
        final CredentialsWithPerms cwp = gridSelectionModel.getSelected();
        if (cwp != null) {
            btnDelete.setEnabled(cwp.isDelete());
            btnEdit.setEnabled(cwp.isEdit());
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
        final CredentialsSecret newSecret = new CredentialsSecret(newCredentials.getUuid());
        final CredentialsWithPerms cwp = new CredentialsWithPerms(newCredentials);
        showDetailsDialog(cwp, newSecret, CreationState.NEW_CREDENTIALS);
    }

    /**
     * Called when the delete button is clicked.
     */
    private void handleDeleteButtonClick() {
        final CredentialsWithPerms selectedCwp = gridSelectionModel.getSelected();

        ConfirmEvent.fire(this, "Are you sure you want to delete the credentials '" +
                                selectedCwp.getCredentials().getName() + "' ?", ok -> {
                if (ok) {
                    deleteCredentials(selectedCwp);
                }
            });
    }

    /**
     * Performs the deletion in the database.
     * @param selectedCwp The currently selected credentials, which are to be deleted.
     */
    private void deleteCredentials(final CredentialsWithPerms selectedCwp) {

        if (selectedCwp != null) {
            restFactory.create(CREDENTIALS_RESOURCE)
                    .method(res -> res.deleteCredentials(selectedCwp.getCredentials().getUuid()))
                    .onSuccess(result -> {
                        if (result.getStatus() == Status.OK) {
                            // Reload the list & select the first item
                            dataProvider.refresh();
                            final CredentialsWithPerms firstItem = dataGrid.getVisibleItem(FIRST_ITEM_INDEX);
                            if (firstItem != null) {
                                gridSelectionModel.setSelected(firstItem);
                            }
                        } else {
                            AlertEvent.fireError(parentPresenter,
                                    "Delete Error",
                                    result.getMessage(),
                                    null);
                        }
                    })
                    .onFailure(error -> {
                        AlertEvent.fireError(parentPresenter,
                                "Delete Error",
                                error.getMessage(),
                                null);
                    })
                    .taskMonitorFactory(CredentialsListPresenter.this)
                    .exec();
        }
    }

    /**
     * When edit button is clicked
     */
    private void handleEditButtonClick() {
        final CredentialsWithPerms cwp = gridSelectionModel.getSelected();

        // Check that the user can edit these credentials
        if (cwp.isEdit()) {

            // Get the secret from the server, if they exist
            restFactory.create(CREDENTIALS_RESOURCE)
                    .method(res -> res.getSecret(cwp.getCredentials().getUuid()))
                    .onSuccess(result -> {
                        if (result.getStatus() == Status.OK) {
                            CredentialsSecret secret = result.getSecret();
                            if (secret == null) {
                                // Secret doesn't exist yet, so create new one
                                secret = new CredentialsSecret(cwp.getCredentials().getUuid());
                            }

                            showDetailsDialog(cwp, secret, CreationState.OLD_CREDENTIALS);
                        } else {
                            AlertEvent.fireError(parentPresenter,
                                    "Error getting credential's details",
                                    result.getMessage(),
                                    null);
                        }
                    })
                    .onFailure(error -> {
                        AlertEvent.fireError(parentPresenter,
                                "Error",
                                error.getMessage(),
                                null);
                    })
                    .taskMonitorFactory(CredentialsListPresenter.this)
                    .exec();
        }
    }

    /**
     * Called when adding or editing credentials.
     * @param cwp The credentials to add or edit. Can be null in which case nothing happens.
     * @param secret The secret to use to display the settings.
     * @param creationState Whether these are new or old credentials.
     */
    private void showDetailsDialog(final CredentialsWithPerms cwp,
                                   final CredentialsSecret secret,
                                   final CreationState creationState) {

        if (cwp != null) {
            final Builder builder = ShowPopupEvent.builder(detailsDialog);
            final String caption = creationState == CreationState.NEW_CREDENTIALS ?
                    "New Credentials" : "Edit Credentials";
            detailsDialog.setupDialog(cwp, secret, caption, builder);
            builder.onHideRequest(e -> {
                if (e.isOk()) {
                    if (detailsDialog.isValid()) {
                        final CredentialsSettingsView view = detailsDialog.getCredentialsSettingsView();
                        final CredentialsWithPerms cwpToSave = view.getCredentialsWithPerms();
                        final CredentialsSecret secretToSave = view.getSecret();
                        e.hide();

                        saveSecretAndCredentials(secretToSave, cwpToSave, creationState);
                    } else {
                        final String validationMessage = detailsDialog.getValidationMessage();

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
            }).fire();
        }
    }

    /**
     * Async save of secret, then credentials.
     * @param secret The secret to save.
     * @param cwp The credentials to save.
     * @param creationState Whether these are new or old credentials.
     */
    private void saveSecretAndCredentials(final CredentialsSecret secret,
                                          final CredentialsWithPerms cwp,
                                          final CreationState creationState) {
        if (creationState == CreationState.NEW_CREDENTIALS) {
            final CredentialsCreateRequest request = new CredentialsCreateRequest(cwp.getCredentials(), secret);

            restFactory.create(CREDENTIALS_RESOURCE)
                    .method(res -> res.createCredentials(request))
                    .onSuccess(result -> {
                        if (result.getStatus() == Status.OK) {
                            // Reload the list & select the value returned from the server
                            dataProvider.refresh();
                            // Shouldn't this be async in a callback? Seems to work.
                            gridSelectionModel.setSelected(result.getCredentialsWithPerms());

                        } else {
                            AlertEvent.fireError(parentPresenter,
                                    "Add Credentials Error",
                                    result.getMessage(),
                                    null);
                        }
                    })
                    .onFailure(error -> {
                        AlertEvent.fireError(parentPresenter,
                                "Add Credentials Error",
                                error.getMessage(),
                                null);
                    })
                    .taskMonitorFactory(CredentialsListPresenter.this)
                    .exec();

        } else {
            // Store the secret
            restFactory.create(CREDENTIALS_RESOURCE)
                    .method(res -> res.storeSecret(secret))
                    .onSuccess(result -> {
                        if (result.getStatus() == Status.OK) {
                            saveCredentials(cwp);

                        } else {
                            AlertEvent.fireError(parentPresenter,
                                    "Save Error",
                                    result.getMessage(),
                                    null);
                        }
                    })
                    .onFailure(error -> {
                        AlertEvent.fireError(parentPresenter,
                                "Save Error",
                                error.getMessage(),
                                null);
                    })
                    .taskMonitorFactory(CredentialsListPresenter.this)
                    .exec();
        }
    }

    /**
     * Async save of credentials.
     * @param cwp The credentials to save.
     */
    private void saveCredentials(final CredentialsWithPerms cwp) {

        restFactory.create(CREDENTIALS_RESOURCE)
                .method(res -> res.storeCredentials(cwp.getCredentials()))
                .onSuccess(result -> {
                    if (result.getStatus() == Status.OK) {
                        // Reload the list & select it
                        dataProvider.refresh();
                        // Shouldn't this be in an async callback? Seems to work though.
                        gridSelectionModel.setSelected(cwp);

                    } else {
                        AlertEvent.fireError(parentPresenter,
                                "Save Credentials Error",
                                result.getMessage(),
                                null);
                    }
                })
                .onFailure(error -> {
                    AlertEvent.fireError(parentPresenter,
                            "Save Error",
                            error.getMessage(),
                            null);
                })
                .taskMonitorFactory(CredentialsListPresenter.this)
                .exec();
    }

}
