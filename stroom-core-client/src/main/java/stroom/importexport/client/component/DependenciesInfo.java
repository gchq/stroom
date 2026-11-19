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

package stroom.importexport.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextArea;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Dependency Information dialog's content: a document's details, read only, to copy.
public class DependenciesInfo extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextArea info;

    /// Makes empty content.
    public DependenciesInfo() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param info The details shown.
    public void setInfo(final String info) {
        this.info.setText(info);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DependenciesInfo> {

    }
}
