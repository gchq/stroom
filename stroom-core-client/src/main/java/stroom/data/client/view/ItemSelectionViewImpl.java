/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.data.client.view;

import stroom.data.client.presenter.ItemSelectionPresenter.ItemSelectionView;
import stroom.util.shared.Count;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.Optional;

public class ItemSelectionViewImpl
        extends ViewImpl
        implements ItemSelectionView {

    private static final String NUMBER_FORMAT = "#,###";
    private static final String UNKNOWN_VALUE = "?";

    private final Widget widget;

    @UiField
    Label nameLbl;
    @UiField
    ValueSpinner itemNoSpinner;
    @UiField
    Label itemCountLbl;

    @Inject
    public ItemSelectionViewImpl(final EventBus eventBus,
                                 final Binder binder) {

        widget = binder.createAndBindUi(this);

        itemNoSpinner.setMin(1);
        itemNoSpinner.setMax(Long.MAX_VALUE);
    }


    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        itemNoSpinner.focus();
    }

    private long toOneBased(final long zeroBasedValue) {
        return zeroBasedValue + 1;
    }

    private long toZeroBased(final long oneBasedValue) {
        return oneBasedValue - 1;
    }

    @Override
    public void setName(final String name) {
        nameLbl.setText(name);
    }

    @Override
    public long getItemNo() {
        return toZeroBased(itemNoSpinner.getIntValue());
    }

    @Override
    public void setItemNo(final long itemNo) {
        itemNoSpinner.setValue(toOneBased(itemNo));
    }

    @Override
    public void setTotalItemsCount(final Count<Long> totalItemsCount) {
        final String countStr = getLongValueForLabel(totalItemsCount.asOptional());
        itemCountLbl.setText("of " + countStr);
        if (totalItemsCount.isExact()) {
            itemNoSpinner.setMax(totalItemsCount.getCount());
        } else {
            itemNoSpinner.setMax(Long.MAX_VALUE);
        }
    }

    private String getLongValueForLabel(final Optional<Long> value) {
        // Increment allows for switching from zero to one based
        return value
                .map(val -> {
                    final NumberFormat formatter = NumberFormat.getFormat(NUMBER_FORMAT);
                    return formatter.format(val);
                })
                .orElse(UNKNOWN_VALUE);
    }

    public interface Binder extends UiBinder<Widget, ItemSelectionViewImpl> {

    }
}
