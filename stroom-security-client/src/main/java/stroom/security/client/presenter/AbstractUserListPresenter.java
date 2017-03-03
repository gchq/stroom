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

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.cellview.client.Column;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cell.info.client.FACell;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.security.shared.UserRef;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcon;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.button.client.ImageButtonView;
import stroom.widget.util.client.MultiSelectionModel;

public abstract class AbstractUserListPresenter extends MyPresenterWidget<UserListView> implements UserListUiHandlers {
    private final DataGridView<UserRef> dataGridView;

    public AbstractUserListPresenter(final EventBus eventBus, final UserListView userListView) {
        super(eventBus, userListView);

        dataGridView = new DataGridViewImpl<UserRef>(true);
        userListView.setDatGridView(dataGridView);
        userListView.setUiHandlers(this);

        // Icon
        dataGridView.addColumn(new Column<UserRef, GlyphIcon>(new FACell()) {
            @Override
            public GlyphIcon getValue(final UserRef userRef) {
                if (userRef.isEnabled()) {
                    if (!userRef.isGroup()) {
                        return GlyphIcons.USER;
                    }

                    return GlyphIcons.USER_GROUP;
                }

                if (!userRef.isGroup()) {
                    return GlyphIcons.USER_DISABLED;
                }

                return GlyphIcons.USER_GROUP_DISABLED;
            }
        }, "</br>", 20);

        // Name.
        dataGridView.addResizableColumn(new Column<UserRef, String>(new TextCell()) {
            @Override
            public String getValue(final UserRef userRef) {
                return userRef.getName();
            }
        }, "Name", 350);

        dataGridView.addEndColumn(new EndColumn<UserRef>());
    }

    public ImageButtonView addButton(final String title, final ImageResource enabledImage,
                                     final ImageResource disabledImage, final boolean enabled) {
        return dataGridView.addButton(title, enabledImage, disabledImage, enabled);
    }

    public GlyphButtonView addButton(final GlyphIcon preset) {
        return dataGridView.addButton(preset);
    }

    @Override
    public void changeNameFilter(String name) {

    }

    public MultiSelectionModel<UserRef> getSelectionModel() {
        return dataGridView.getSelectionModel();
    }

    public DataGridView<UserRef> getDataGridView() {
        return dataGridView;
    }
}
