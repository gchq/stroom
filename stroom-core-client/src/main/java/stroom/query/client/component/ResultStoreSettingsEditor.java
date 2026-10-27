/*
 * Copyright 2023 Crown Copyright
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

package stroom.query.client.component;

import stroom.query.api.LifespanInfo;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.view.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A result store's settings: how long its search process and its results live, and whether
/// closing the tab or the window ends them.
public class ResultStoreSettingsEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox searchProcessTimeToIdle;
    @UiField
    TextBox searchProcessTimeToLive;
    @UiField
    TickBox searchProcessDestroyOnTabClose;
    @UiField
    TickBox searchProcessDestroyOnWindowClose;

    @UiField
    TextBox storeTimeToIdle;
    @UiField
    TextBox storeTimeToLive;
    @UiField
    TickBox storeDestroyOnTabClose;
    @UiField
    TickBox storeDestroyOnWindowClose;

    /// Makes empty settings.
    public ResultStoreSettingsEditor() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Focuses the first setting.
    public void focus() {
        searchProcessTimeToIdle.setFocus(true);
    }

    /// Shows a store's lifespans.
    ///
    /// @param searchProcessLifespan How long the search process lives.
    /// @param storeLifespan         How long the results live.
    public void setLifespans(final LifespanInfo searchProcessLifespan, final LifespanInfo storeLifespan) {
        searchProcessTimeToIdle.setValue(searchProcessLifespan.getTimeToIdle());
        searchProcessTimeToLive.setValue(searchProcessLifespan.getTimeToLive());
        searchProcessDestroyOnTabClose.setValue(searchProcessLifespan.isDestroyOnTabClose());
        searchProcessDestroyOnWindowClose.setValue(searchProcessLifespan.isDestroyOnWindowClose());

        storeTimeToIdle.setValue(storeLifespan.getTimeToIdle());
        storeTimeToLive.setValue(storeLifespan.getTimeToLive());
        storeDestroyOnTabClose.setValue(storeLifespan.isDestroyOnTabClose());
        storeDestroyOnWindowClose.setValue(storeLifespan.isDestroyOnWindowClose());
    }

    /// @return How long the search process lives, as entered.
    public LifespanInfo getSearchProcessLifespan() {
        return new LifespanInfo(searchProcessTimeToIdle.getText(),
                searchProcessTimeToLive.getText(),
                searchProcessDestroyOnTabClose.getValue(),
                searchProcessDestroyOnWindowClose.getValue());
    }

    /// @return How long the results live, as entered.
    public LifespanInfo getStoreLifespan() {
        return new LifespanInfo(storeTimeToIdle.getText(),
                storeTimeToLive.getText(),
                storeDestroyOnTabClose.getValue(),
                storeDestroyOnWindowClose.getValue());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ResultStoreSettingsEditor> {

    }
}
