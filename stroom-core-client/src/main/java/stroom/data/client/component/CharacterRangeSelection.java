/*
 * Copyright 2020 Crown Copyright
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

import stroom.docref.HasDisplayValue;
import stroom.util.shared.Count;
import stroom.util.shared.DataRange;
import stroom.widget.component.client.Component;
import stroom.widget.linecolinput.client.LineColInput;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.text.client.Label;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The "Set Source Range" dialog's content: where the range of the source shown starts (a line and
/// column, or a character offset) and ends (a line and column, a character offset, or a number of
/// characters), each counted from 1 and inclusive, as the user sees them. Its markup is
/// `CharacterRangeSelection.ui.xml`; [CharacterRangeSelectionController] shows it.
public class CharacterRangeSelection extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final NumberFormat NUMBER_FORMATTER = NumberFormat.getFormat("#,###");

    @UiField
    Label lblTotalCharCount;
    @UiField
    SelectionBox<From> fromType;
    @UiField
    LineColInput fromLineCol;
    @UiField
    ValueSpinner fromCharOffset;
    @UiField
    SelectionBox<To> toType;
    @UiField
    LineColInput toLineCol;
    @UiField
    ValueSpinner toCharOffset;
    @UiField
    ValueSpinner toCharCount;

    /// Makes the dialog's content, with no total.
    public CharacterRangeSelection() {
        initWidget(BINDER.createAndBindUi(this));
        setMax(Long.MAX_VALUE);

        fromType.addItem(From.LINE_COL);
        fromType.addItem(From.OFFSET);
        toType.addItem(To.LINE_COL);
        toType.addItem(To.OFFSET);
        toType.addItem(To.COUNT);
        fromType.addValueChangeHandler(event -> updateFromSelection());
        toType.addValueChangeHandler(event -> updateToSelection());
    }

    /// Puts the focus on From's type.
    public void focus() {
        fromType.focus();
    }

    /// @return The range entered, zero based.
    public DataRange getDataRange() {
        final DataRange.Builder dataRangeBuilder = DataRange.builder();
        switch (fromType.getValue()) {
            case LINE_COL:
                switch (toType.getValue()) {
                    case LINE_COL:
                        fromLineCol.getLocation().ifPresent(dataRangeBuilder::fromLocation);
                        toLineCol.getLocation().ifPresent(dataRangeBuilder::toLocation);
                        break;
                    case COUNT:
                        fromLineCol.getLocation().ifPresent(dataRangeBuilder::fromLocation);
                        dataRangeBuilder.withLength(toCharCount.getValue());
                        break;
                }
                break;
            case OFFSET:
                switch (toType.getValue()) {
                    case OFFSET:
                        dataRangeBuilder
                                .fromCharOffset(fromCharOffset.getValue() - 1L)
                                .toCharOffset(toCharOffset.getValue() - 1L);
                        break;
                    case COUNT:
                        dataRangeBuilder
                                .fromCharOffset(fromCharOffset.getValue() - 1L)
                                .withLength(toCharCount.getValue());
                        break;
                }
                break;
        }
        return dataRangeBuilder.build();
    }

    /// @param dataRange The range to show, zero based.
    public void setDataRange(final DataRange dataRange) {
        fromType.setValue(dataRange.getOptLocationFrom().isPresent()
                ? From.LINE_COL
                : From.OFFSET);
        updateFromSelection();

        dataRange.getOptLocationFrom().ifPresent(fromLineCol::setValue);
        dataRange.getOptCharOffsetFrom().ifPresent(offset -> fromCharOffset.setValue(offset + 1));
        dataRange.getOptLocationTo().ifPresent(toLineCol::setValue);
        dataRange.getOptCharOffsetTo().ifPresent(offset -> toCharOffset.setValue(offset + 1));
        dataRange.getOptLength().ifPresent(toCharCount::setValue);
    }

    /// Shows how many characters there are, which is the most an offset or count can be if it is
    /// exact.
    ///
    /// @param totalCharCount The number of characters.
    public void setTotalCharsCount(final Count<Long> totalCharCount) {
        setMax(totalCharCount.isExact() && totalCharCount.getCount() != null
                ? totalCharCount.getCount()
                : Long.MAX_VALUE);
        lblTotalCharCount.setText((totalCharCount.isExact()
                ? ""
                : "~") + NUMBER_FORMATTER.format(totalCharCount.getCount()));
    }

    private void setMax(final long max) {
        fromCharOffset.setMax(max);
        toCharOffset.setMax(max);
        toCharCount.setMax(max);
    }

    // To offers what goes with From: a line and column with a line and column, an offset with an
    // offset, and a count with either
    private void updateFromSelection() {
        final From from = fromType.getValue();
        fromLineCol.setVisible(From.LINE_COL.equals(from));
        fromCharOffset.setVisible(From.OFFSET.equals(from));

        final To to = toType.getValue();
        toType.clear();
        switch (from) {
            case LINE_COL:
                toType.addItem(To.LINE_COL);
                toType.addItem(To.COUNT);
                toType.setValue(to == To.COUNT
                        ? to
                        : To.LINE_COL);
                break;
            case OFFSET:
                toType.addItem(To.OFFSET);
                toType.addItem(To.COUNT);
                toType.setValue(to == To.COUNT
                        ? to
                        : To.OFFSET);
                break;
        }
        updateToSelection();
    }

    private void updateToSelection() {
        final To to = toType.getValue();
        toLineCol.setVisible(To.LINE_COL.equals(to));
        toCharOffset.setVisible(To.OFFSET.equals(to));
        toCharCount.setVisible(To.COUNT.equals(to));
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CharacterRangeSelection> {

    }


    // --------------------------------------------------------------------------------


    /// How the range's start is given.
    public enum From implements HasDisplayValue {
        LINE_COL("Line:Col (inc)"),
        OFFSET("Character Offset (inc)");

        private final String displayValue;

        From(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }


    // --------------------------------------------------------------------------------


    /// How the range's end is given.
    public enum To implements HasDisplayValue {
        LINE_COL("Line:Col (inc)"),
        OFFSET("Character Offset (inc)"),
        COUNT("Character Count");

        private final String displayValue;

        To(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
