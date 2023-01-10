/*
 * Copyright 2016 Crown Copyright
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

import stroom.cell.info.client.SvgCell;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.security.shared.User;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractUserListPresenter
        extends MyPresenterWidget<UserListView>
        implements UserListUiHandlers {

    private final DataGridView<User> dataGridView;

    private final List<Column<User, ?>> columns = new ArrayList<>();
    private boolean isExternalIdentityProvider = false;
    private Boolean lastIncludeUserInfoColsVal = null;

    public AbstractUserListPresenter(final EventBus eventBus,
                                     final UserListView userListView,
                                     final UiConfigCache uiConfigCache) {
        super(eventBus, userListView);

        dataGridView = new DataGridViewImpl<>(true);
        userListView.setDatGridView(dataGridView);
        userListView.setUiHandlers(this);

        refresh();

        uiConfigCache.get().onSuccess(extendedUiConfig -> {
            isExternalIdentityProvider = extendedUiConfig.isExternalIdentityProvider();
            refresh();
        });
    }
    
    public void refresh() {
        final boolean includeUserInfoCols = isExternalIdentityProvider
                && includeAdditionalUserInfo();

        if (this.lastIncludeUserInfoColsVal == null || this.lastIncludeUserInfoColsVal != includeUserInfoCols) {
            setupColumns();
            this.lastIncludeUserInfoColsVal = includeUserInfoCols;
        }
    }

    private void setupColumns() {
        // Remove any existing cols ready to add cols back in
        for (final Column<User, ?> column : columns) {
            dataGridView.removeColumn(column);
        }
        columns.clear();
        
        // Icon
        final Column<User, Preset> iconCol = new Column<User, Preset>(new SvgCell()) {
            @Override
            public Preset getValue(final User userRef) {
                if (!userRef.isGroup()) {
                    return SvgPresets.USER;
                }

                return SvgPresets.USER_GROUP;
            }
        };
        dataGridView.addColumn(iconCol, "</br>", 20);
        columns.add(iconCol);

        // Name.
        final Column<User, String> uniqueIdentityCol = new Column<User, String>(new TextCell()) {
            @Override
            public String getValue(final User userRef) {
                return userRef.getName();
            }
        };
        dataGridView.addResizableColumn(uniqueIdentityCol, "Unique Identity", 280);
        columns.add(uniqueIdentityCol);

        if (includeAdditionalUserInfo()) {
            // Preferred User Name
            final Column<User, String> preferredUsernameCol = new Column<User, String>(new TextCell()) {
                @Override
                public String getValue(final User userRef) {
                    return userRef.getPreferredUsername();
                }
            };
            dataGridView.addResizableColumn(preferredUsernameCol, "Preferred Username", 250);
            columns.add(preferredUsernameCol);

            // Full name
            final Column<User, String> fullNameCol = new Column<User, String>(new TextCell()) {
                @Override
                public String getValue(final User userRef) {
                    return userRef.getFullName();
                }
            };
            dataGridView.addResizableColumn(fullNameCol, "Full Name", 350);
            columns.add(fullNameCol);
        }

        final EndColumn<User> endCol = new EndColumn<>();
        dataGridView.addEndColumn(new EndColumn<User>());
        columns.add(endCol);
    }

    public ButtonView addButton(final Preset preset) {
        return dataGridView.addButton(preset);
    }

    @Override
    public void changeNameFilter(String name) {

    }

    public MultiSelectionModel<User> getSelectionModel() {
        return dataGridView.getSelectionModel();
    }

    public DataGridView<User> getDataGridView() {
        return dataGridView;
    }

    public boolean includeAdditionalUserInfo() {
        return true;
    }
}
