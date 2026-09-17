/*
 * Copyright 2026 Crown Copyright
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
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.identity.shared.SigningKeyResource;
import stroom.security.identity.shared.SigningKeyRow;
import stroom.security.identity.shared.SigningKeyStatus;
import stroom.svg.client.Preset;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;

/**
 * The internal identity provider's signing keys.
 * <p>
 * Unlike the other lists here there is no quick filter and no paging: a deployment holds one signing key and
 * perhaps one or two on their way out, so both would be furniture rather than help.
 * </p>
 */
public class SigningKeyListPresenter extends MyPresenterWidget<PagerView> {

    private static final SigningKeyResource SIGNING_KEY_RESOURCE = GWT.create(SigningKeyResource.class);

    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private final PagerView pagerView;
    private final MyDataGrid<SigningKeyRow> dataGrid;
    private final MultiSelectionModelImpl<SigningKeyRow> selectionModel;

    @Inject
    public SigningKeyListPresenter(final EventBus eventBus,
                                   final PagerView pagerView,
                                   final RestFactory restFactory,
                                   final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, pagerView);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        this.pagerView = pagerView;
        this.dataGrid = new MyDataGrid<>(this);
        this.dataGrid.setTableName("Signing Keys");
        this.selectionModel = dataGrid.addDefaultSelectionModel(true);
        pagerView.setDataWidget(dataGrid);

        initTableColumns();
    }

    public MultiSelectionModelImpl<SigningKeyRow> getSelectionModel() {
        return selectionModel;
    }

    public ButtonView addButton(final Preset preset) {
        return pagerView.addButton(preset);
    }

    /**
     * Two columns and no more. Nothing here describes the key itself - the stored key includes its private
     * material, so the server never sends anything but what the key is doing and when.
     */
    private void initTableColumns() {
        final Column<SigningKeyRow, String> statusColumn = DataGridUtil.textColumnBuilder(
                        this::describeStatus)
                .build();
        dataGrid.addResizableColumn(
                statusColumn,
                DataGridUtil.headingBuilder("Status")
                        .withToolTip("Active signs new tokens and has no scheduled end: rotation replaces "
                                     + "it, and only then does a wind-down date exist. Retired still "
                                     + "verifies tokens already issued, until the date shown. Revoked was "
                                     + "withdrawn by an administrator.")
                        .build(),
                300);

        final Column<SigningKeyRow, String> issuedColumn = DataGridUtil.textColumnBuilder(
                        (SigningKeyRow row) -> dateTimeFormatter.format(row.getIssuedMs()))
                .build();
        dataGrid.addAutoResizableColumn(
                issuedColumn,
                DataGridUtil.headingBuilder("Issued")
                        .withToolTip("When this key was created.")
                        .build(),
                ColumnSizeConstants.DATE_COL);
    }

    /**
     * The trust-window end rides with the status for the two states that have one, rather than sitting in
     * an "Expires" column. These keys do not expire on a schedule - the active key has no date at all until
     * rotation replaces it - so a column shaped like a certificate lifetime answered a question the most
     * important row could not, and "While active" was that column admitting it.
     */
    private String describeStatus(final SigningKeyRow row) {
        final SigningKeyStatus status = row.getStatus();
        if (status == null) {
            return "";
        }
        if (row.getExpiresMs() == null) {
            return status.getDisplayValue();
        }
        return status.getDisplayValue()
               + " (trusted until " + dateTimeFormatter.format(row.getExpiresMs()) + ")";
    }

    public void refresh() {
        restFactory
                .create(SIGNING_KEY_RESOURCE)
                .method(SigningKeyResource::list)
                .onSuccess(this::setData)
                .onFailure(restError -> {
                    AlertEvent.fireError(this, restError.getMessage(), null);
                    setData(List.of());
                })
                .taskMonitorFactory(this)
                .exec();
    }

    private void setData(final List<SigningKeyRow> rows) {
        dataGrid.setRowData(0, rows);
        dataGrid.setRowCount(rows.size(), true);
        if (rows.isEmpty()) {
            selectionModel.clear();
        }
    }
}
