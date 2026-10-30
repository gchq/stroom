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

package stroom.statistics.impl.sql.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The New Field and Edit Field dialog's content: a statistic field's name.
public class StatisticsFieldEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox name;

    /// Makes the content, with no name.
    public StatisticsFieldEditor() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The name entered.
    public String getFieldName() {
        return name.getText();
    }

    /// @param fieldName The name to show.
    public void setFieldName(final String fieldName) {
        name.setText(fieldName);
    }

    /// Puts the focus on the name.
    public void focus() {
        name.setFocus(true);
    }

    interface Binder extends UiBinder<Widget, StatisticsFieldEditor> {

    }
}
