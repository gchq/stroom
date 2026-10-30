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

package stroom.data.client.component;

import stroom.util.shared.Count;
import stroom.widget.component.client.Component;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The item selection dialog's content: the number of the item to show (one based, as the user
/// sees it), of how many there are. Its markup is `ItemSelection.ui.xml`; [ItemSelectionController]
/// shows it.
public class ItemSelection extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String NUMBER_FORMAT = "#,###";
    private static final String UNKNOWN_VALUE = "?";

    @UiField
    ValueSpinner itemNoSpinner;
    @UiField
    SpanElement itemCount;

    /// Makes the dialog's content with no count of items.
    public ItemSelection() {
        initWidget(BINDER.createAndBindUi(this));
        itemNoSpinner.setMax(Long.MAX_VALUE);
    }

    /// Puts the focus on the item number.
    public void focus() {
        itemNoSpinner.focus();
    }

    /// @return The item chosen, zero based.
    public long getItemNo() {
        return itemNoSpinner.getValue() - 1;
    }

    /// @param itemNo The item to show, zero based.
    public void setItemNo(final long itemNo) {
        itemNoSpinner.setValue(itemNo + 1);
    }

    /// Shows how many items there are, which is the most that can be chosen if it is exact.
    ///
    /// @param totalItemsCount The number of items.
    public void setTotalItemsCount(final Count<Long> totalItemsCount) {
        itemCount.setInnerText(totalItemsCount.asOptional()
                .map(count -> NumberFormat.getFormat(NUMBER_FORMAT).format(count))
                .orElse(UNKNOWN_VALUE));
        itemNoSpinner.setMax(totalItemsCount.isExact()
                ? totalItemsCount.getCount()
                : Long.MAX_VALUE);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ItemSelection> {

    }
}
