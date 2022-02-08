package stroom.data.client.view;

import stroom.data.client.presenter.CharacterRangeSelectionPresenter.CharacterRangeSelectionView;
import stroom.docref.HasDisplayValue;
import stroom.item.client.ItemListBox;
import stroom.util.shared.Count;
import stroom.util.shared.DataRange;
import stroom.widget.linecolinput.client.LineColInput;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class CharacterRangeSelectionViewImpl
        extends ViewImpl
        implements CharacterRangeSelectionView {

    private static final long ONE_TO_ZERO_BASED_DECREMENT = 1L;
    private static final NumberFormat numberFormatter = NumberFormat.getFormat("#,###");

    private final Widget widget;

    @UiField
    Label lblTotalCharCount;

    @UiField
    ItemListBox<From> fromType;
    @UiField
    LineColInput fromLineCol;
    @UiField
    ValueSpinner fromCharOffset;

    @UiField
    ItemListBox<To> toType;
    @UiField
    LineColInput toLineCol;
    @UiField
    ValueSpinner toCharOffset;
    @UiField
    ValueSpinner toCharCount;

    private Count<Long> totalCharCount = Count.of(0L, false);

    @Inject
    public CharacterRangeSelectionViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        setInitialMinMax();

        fromType.clear();
        fromType.addItem(From.LINE_COL);
        fromType.addItem(From.OFFSET);

        toType.clear();
        toType.addItem(To.LINE_COL);
        toType.addItem(To.OFFSET);
        toType.addItem(To.COUNT);
    }

    private void setInitialMinMax() {
        fromCharOffset.setMin(1);
        fromCharOffset.setMax(Long.MAX_VALUE);

        toCharOffset.setMin(1);
        toCharOffset.setMax(Long.MAX_VALUE);

        toCharCount.setMin(1);
        toCharCount.setMax(Long.MAX_VALUE);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        fromType.focus();
    }

    @Override
    public DataRange getDataRange() {
        final DataRange.Builder dataRangeBuilder = DataRange.builder();

        switch (fromType.getSelectedItem()) {
            case LINE_COL:

                switch (toType.getSelectedItem()) {
                    case LINE_COL:
                        fromLineCol.getLocation().ifPresent(dataRangeBuilder::fromLocation);
                        toLineCol.getLocation().ifPresent(dataRangeBuilder::toLocation);
                        break;

                    case COUNT:
                        fromLineCol.getLocation().ifPresent(dataRangeBuilder::fromLocation);
                        dataRangeBuilder.withLength((long) toCharCount.getValue());

                        break;
                }

                break;
            case OFFSET:
                switch (toType.getSelectedItem()) {
                    case OFFSET:
                        dataRangeBuilder
                                .fromCharOffset(toZeroBased(fromCharOffset.getValue()))
                                .toCharOffset((long) toCharOffset.getValue() - ONE_TO_ZERO_BASED_DECREMENT);

                        break;
                    case COUNT:
                        dataRangeBuilder
                                .fromCharOffset(toZeroBased(fromCharOffset.getValue()))
                                .withLength((long) toCharCount.getValue());

                        break;
                }

                break;
        }

        return dataRangeBuilder.build();
    }

    @Override
    public void setDataRange(final DataRange dataRange) {
        setEnabledAndCheckedStates(dataRange);
        updateFromSelection();

        dataRange.getOptLocationFrom().ifPresent(location -> {
            fromLineCol.setValue(location);
        });
        dataRange.getOptCharOffsetFrom().ifPresent(offset -> {
            fromCharOffset.setValue(toOneBased(offset));
        });
        dataRange.getOptLocationTo().ifPresent(location -> {
            toLineCol.setValue(location);
        });
        dataRange.getOptCharOffsetTo().ifPresent(offset -> {
            toCharOffset.setValue(toOneBased(offset));
        });
        dataRange.getOptLength().ifPresent(count -> {
            toCharCount.setValue(count);
        });
    }


    private void setEnabledAndCheckedStates(final DataRange dataRange) {
        if (dataRange.getOptLocationFrom().isPresent()) {
            fromType.setSelectedItem(From.LINE_COL);
        } else {
            fromType.setSelectedItem(From.OFFSET);
        }
    }

    private void updateFromSelection() {
        final From from = fromType.getSelectedItem();
        fromLineCol.setVisible(From.LINE_COL.equals(from));
        fromCharOffset.setVisible(From.OFFSET.equals(from));

        final To to = toType.getSelectedItem();
        toType.clear();
        switch (from) {
            case LINE_COL:
                toType.addItem(To.LINE_COL);
                toType.addItem(To.COUNT);
                if (to == To.COUNT) {
                    toType.setSelectedItem(to);
                } else {
                    toType.setSelectedItem(To.LINE_COL);
                }
                break;
            case OFFSET:
                toType.addItem(To.OFFSET);
                toType.addItem(To.COUNT);
                if (to == To.COUNT) {
                    toType.setSelectedItem(to);
                } else {
                    toType.setSelectedItem(To.OFFSET);
                }
                break;
        }
        updateToSelection();
    }

    private void updateToSelection() {
        final To to = toType.getSelectedItem();
        toLineCol.setVisible(To.LINE_COL.equals(to));
        toCharOffset.setVisible(To.OFFSET.equals(to));
        toCharCount.setVisible(To.COUNT.equals(to));
    }

    private void updateCountLabels() {
        final String prefix = totalCharCount.isExact()
                ? ""
                : "~";

        lblTotalCharCount.setText(prefix + numberFormatter.format(totalCharCount.getCount()));
    }

    @Override
    public void setTotalCharsCount(final Count<Long> totalCharCount) {
        this.totalCharCount = totalCharCount;
        if (totalCharCount.isExact() && totalCharCount.getCount() != null) {
            fromCharOffset.setMax(totalCharCount.getCount());
            toCharOffset.setMax(totalCharCount.getCount());
            toCharCount.setMax(totalCharCount.getCount());
        } else {
            fromCharOffset.setMax(Long.MAX_VALUE);
            toCharOffset.setMax(Long.MAX_VALUE);
            toCharCount.setMax(Long.MAX_VALUE);
        }
        updateCountLabels();
    }

    private long toOneBased(final long zeroBasedValue) {
        return zeroBasedValue + 1;
    }

    private long toZeroBased(final long oneBasedValue) {
        return oneBasedValue - 1;
    }

    public interface Binder extends UiBinder<Widget, CharacterRangeSelectionViewImpl> {

    }

    @UiHandler("fromType")
    public void onFromTypeChange(final SelectionEvent<From> event) {
        updateFromSelection();
    }

    @UiHandler("toType")
    public void onToTypeChange(final SelectionEvent<To> event) {
        updateToSelection();
    }

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
