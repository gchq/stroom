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

package stroom.dashboard.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextArea;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;

/// A column's include and exclude filter, side by side: for each, regular expressions, one per line,
/// and a list of dictionaries.
public class IncludeExcludeFilterEditor extends Component implements Focus {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextArea includeText;
    @UiField
    TextArea excludeText;
    @UiField
    IncludeExcludeFilterDictionary includeDictionaries;
    @UiField
    IncludeExcludeFilterDictionary excludeDictionaries;

    /// Makes an empty filter.
    public IncludeExcludeFilterEditor() {
        initWidget(BINDER.createAndBindUi(this));
    }

    @Override
    public void focus() {
        includeText.setFocus(true);
    }

    /// @return The include regular expressions, one per line.
    public String getIncludes() {
        return includeText.getText();
    }

    /// @param includes The include regular expressions, one per line.
    public void setIncludes(final String includes) {
        includeText.setText(includes);
    }

    /// @return The exclude regular expressions, one per line.
    public String getExcludes() {
        return excludeText.getText();
    }

    /// @param excludes The exclude regular expressions, one per line.
    public void setExcludes(final String excludes) {
        excludeText.setText(excludes);
    }

    /// @return The include dictionaries' list, for its controller to attach to.
    public IncludeExcludeFilterDictionary getIncludeDictionaries() {
        return includeDictionaries;
    }

    /// @return The exclude dictionaries' list, for its controller to attach to.
    public IncludeExcludeFilterDictionary getExcludeDictionaries() {
        return excludeDictionaries;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, IncludeExcludeFilterEditor> {

    }
}
