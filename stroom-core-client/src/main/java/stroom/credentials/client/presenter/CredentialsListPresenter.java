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

package stroom.credentials.client.presenter;

import stroom.ai.shared.KeyStoreType;
import stroom.alert.client.event.ConfirmEvent;
import stroom.credentials.client.presenter.CredentialEditPresenter.CreationState;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.FindCredentialRequest;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.user.cellview.client.LoadingStateChangeEvent.LoadingState;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

/**
 * Shows the list of credentials.
 */
public class CredentialsListPresenter extends MyPresenterWidget<PagerView> {

    /**
     * Used to talk to the server
     */
    private final CredentialClient credentialClient;

    /**
     * Formats the expiry date
     */
    private final DateTimeFormatter dateTimeFormatter;

    /**
     * Where the data grid gets its data from
     */
    private RestDataProvider<CredentialWithPerms, ResultPage<CredentialWithPerms>> dataProvider;

    /**
     * List of credentials
     */
    private final MyDataGrid<CredentialWithPerms> dataGrid;

    /**
     * What is selected in the list?
     */
    private final MultiSelectionModel<CredentialWithPerms> gridSelectionModel;

    /**
     * Button in view to add another credential
     */
    private final ButtonView btnAdd;

    /**
     * Button in view to delete a credential
     */
    private final ButtonView btnDelete;

    /**
     * Button in view to edit a credential
     */
    private final ButtonView btnEdit;

    /**
     * Dialog to edit the credentials
     */
    private final Provider<CredentialEditPresenter> credentialEditPresenterProvider;

    /**
     * Permissions client
     */
    private final ClientSecurityContext securityContext;

    /**
     * Flag to set whether something is selected by default
     */
    private boolean defaultSelection = true;

    /**
     * Index of the first item in the list of credentials
     */
    private static final int FIRST_ITEM_INDEX = 0;

    /**
     * ID of the presenter for the list of credentials
     */
    public static final String CREDENTIALS_LIST = "CREDENTIALS_LIST";

    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    public CredentialsListPresenter(final EventBus eventBus,
                                    final PagerView view,
                                    final CredentialClient credentialClient,
                                    final Provider<CredentialEditPresenter> credentialEditPresenterProvider,
                                    final DateTimeFormatter dateTimeFormatter,
                                    final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.credentialClient = credentialClient;
        this.credentialEditPresenterProvider = credentialEditPresenterProvider;
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
    private void initColumns(final MyDataGrid<CredentialWithPerms> grid) {
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(this::getCredentialsName).build(),
                DataGridUtil.headingBuilder("Name").withToolTip("Name of credentials").build(),
                280);

        grid.addResizableColumn(DataGridUtil.textColumnBuilder(this::getCredentialsType).build(),
                DataGridUtil.headingBuilder("Credential Type").withToolTip("Type of credential").build(),
                150);

        grid.addResizableColumn(DataGridUtil.textColumnBuilder(this::getKeyStoreType).build(),
                DataGridUtil.headingBuilder("Key Store Type").withToolTip("Type of keystore").build(),
                150);

        grid.addResizableColumn(DataGridUtil.textColumnBuilder(this::getCredentialsExpires).build(),
                DataGridUtil.headingBuilder("Expires").withToolTip("When these credentials expire").build(),
                190);

        grid.addEndColumn(new EndColumn<>());
    }

    /**
     * Provides the name of the credentials to the data grid column.
     */
    private String getCredentialsName(final CredentialWithPerms cwp) {
        return cwp.getCredential().getName();
    }

    /**
     * Provides the expiry date of the credentials to the data grid column.
     */
    private String getCredentialsExpires(final CredentialWithPerms cwp) {
        return NullSafe.getOrElse(cwp,
                CredentialWithPerms::getCredential,
                Credential::getExpiryTimeMs,
                dateTimeFormatter::format,
                "Never");
    }

    /**
     * Provides the type of the credentials to the data grid column.
     */
    private String getCredentialsType(final CredentialWithPerms cwp) {
        return cwp.getCredential().getCredentialType().getDisplayValue();
    }

    private String getKeyStoreType(final CredentialWithPerms cwp) {
        return NullSafe.getOrElse(cwp,
                CredentialWithPerms::getCredential,
                Credential::getKeyStoreType,
                KeyStoreType::getDisplayValue,
                "");
    }

    /**
     * Sets up the data provider for the list of credentials.
     */
    @SuppressWarnings("checkstyle:LineLength")
    private RestDataProvider<CredentialWithPerms, ResultPage<CredentialWithPerms>> createDataProvider(final EventBus eventBus,
                                                                                                      final PagerView view) {
        return new RestDataProvider<>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<CredentialWithPerms>> dataConsumer,
                                final RestErrorHandler restErrorHandler) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                final FindCredentialRequest request = new FindCredentialRequest(pageRequest,
                        null,
                        null,
                        null,
                        DocumentPermission.VIEW);
                credentialClient.findCredentialsWithPermissions(request, dataConsumer, restErrorHandler, view);
            }
        };
    }

    /**
     * Called when the presenter gets loaded into the UI. Sets up the event handlers.
     */
    @Override
    protected void onBind() {
        super.onBind();

        // Avoid permissions issues if the user doesn't have the AppPermission to see the data
        if (securityContext.hasAppPermission(AppPermission.CREDENTIALS)) {
            dataProvider = createDataProvider(super.getEventBus(), super.getView());
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
            if (event.getLoadingState() == LoadingState.LOADED ||
                event.getLoadingState() == LoadingState.PARTIALLY_LOADED) {
                if (defaultSelection) {
                    if (gridSelectionModel.getSelected() == null && dataGrid.getRowCount() > 0) {
                        final CredentialWithPerms cwp = dataGrid.getVisibleItem(FIRST_ITEM_INDEX);
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
     *
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
            credentialClient.getCredentialByUuid(uuid, this::setSelectedCredential, this);
        } else {
            setSelectedCredential(null);
        }
    }

    /**
     * Sets the selected credentials in the UI.
     *
     * @param credential The credential to show as selected. Can be null if nothing is selected.
     */
    private void setSelectedCredential(final Credential credential) {
        gridSelectionModel.setSelected(new CredentialWithPerms(credential, true, true));
    }

    /**
     * @return The ID of the currently selected credentials, or null if nothing is selected.
     */
    public String getSelectedCredentialsId() {
        final CredentialWithPerms selectedCwp = gridSelectionModel.getSelected();
        if (selectedCwp != null) {
            return selectedCwp.getCredential().getUuid();
        } else {
            return null;
        }
    }

    /**
     * Updates the UI state.
     */
    private void updateState() {
        final CredentialWithPerms cwp = gridSelectionModel.getSelected();
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
        credentialClient.createDocRef(docRef -> {
            credentialEditPresenterProvider.get()
                    .show(docRef, null, CreationState.NEW_CREDENTIALS, this::afterEdit);
        }, this);
    }

    private void afterEdit(final Credential credential) {
        // Reload the list & select it
        dataProvider.refresh();
        setSelectedCredential(credential);
    }

    /**
     * Called when the delete button is clicked.
     */
    private void handleDeleteButtonClick() {
        final CredentialWithPerms selectedCwp = gridSelectionModel.getSelected();
        ConfirmEvent.fire(this,
                "Are you sure you want to delete the credentials '" +
                selectedCwp.getCredential().getName() +
                "' ?",
                ok -> {
                    if (ok) {
                        deleteCredentials(selectedCwp);
                    }
                });
    }

    /**
     * Performs the deletion in the database.
     *
     * @param selectedCwp The currently selected credentials, which are to be deleted.
     */
    private void deleteCredentials(final CredentialWithPerms selectedCwp) {
        if (selectedCwp != null) {
            credentialClient.deleteCredentials(selectedCwp.getCredential().getUuid(), result -> {
                // Reload the list & select the first item
                dataProvider.refresh();
                final CredentialWithPerms firstItem = dataGrid.getVisibleItem(FIRST_ITEM_INDEX);
                if (firstItem != null) {
                    gridSelectionModel.setSelected(firstItem);
                }
            }, this);
        }
    }

    /**
     * When edit button is clicked
     */
    private void handleEditButtonClick() {
        final CredentialWithPerms cwp = gridSelectionModel.getSelected();

        // Check that the user can edit these credentials
        if (cwp.isEdit()) {
            credentialEditPresenterProvider.get().show(cwp.getCredential().asDocRef(),
                    cwp,
                    CreationState.OLD_CREDENTIALS,
                    this::afterEdit);
        }
    }
}
