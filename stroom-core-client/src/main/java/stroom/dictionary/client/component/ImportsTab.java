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

package stroom.dictionary.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A dictionary's Imports tab: the dictionaries it imports above the effective words of the one
/// selected, each a list its controller attaches to.
public class ImportsTab extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DocRefList imports;
    @UiField
    EffectiveWordsList words;

    /// Makes the tab, with no lists.
    public ImportsTab() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The dictionaries imported, which its controller attaches the list's controller to.
    public DocRefList getImports() {
        return imports;
    }

    /// @return The effective words of the import selected, which its controller attaches the
    ///         list's controller to.
    public EffectiveWordsList getWords() {
        return words;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ImportsTab> {

    }
}
