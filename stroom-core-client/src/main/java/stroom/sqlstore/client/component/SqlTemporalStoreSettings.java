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

package stroom.sqlstore.client.component;

import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A SQL temporal store's Settings tab: how many entries it holds, and a button to remove them all,
/// which can't be pressed in a store the user can't change.
public class SqlTemporalStoreSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Label count;
    @UiField
    Button reset;

    /// Makes an empty tab.
    public SqlTemporalStoreSettings() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param count The number of entries the store holds.
    public void setCount(final long count) {
        this.count.setText(String.valueOf(count));
    }

    /// @param onReset What to run when Reset Store is pressed.
    public void setOnReset(final Runnable onReset) {
        reset.setOnClick(onReset);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        reset.setEnabled(!readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SqlTemporalStoreSettings> {

    }
}
