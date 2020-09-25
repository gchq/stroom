package stroom.data.client.view;

import stroom.data.client.presenter.CharacterRangeSelectionPresenter.CharacterRangeSelectionView;
import stroom.util.shared.DataRange;
import stroom.util.shared.RowCount;
import stroom.widget.linecolinput.client.LineColInput;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.ViewImpl;

public class CharacterRangeSelectionViewImpl
        extends ViewImpl
        implements CharacterRangeSelectionView {

    private static final String RADIO_BUTTON_GROUP = "RadioBtnGrp";
    private static final long ZERO_TO_ONE_BASED_INCREMENT = 1L;
    private static final long ONE_TO_ZERO_BASED_DECREMENT = 1L;

    private final Widget widget;

    @UiField
    Label lblTotalCharCount;

//    @UiField
//    Grid sourceGrid;
//    @UiField
//    Grid grid;

    @UiField(provided = true)
    RadioButton radioLocationToLocation;
    @UiField
    Label lblLineColFrom1;
    @UiField
    LineColInput lineColFrom1;
    @UiField
    Label lblLineColTo;
    @UiField
    LineColInput lineColTo;

    @UiField(provided = true)
    RadioButton radioLocationWithCount;
    @UiField
    Label lblLineColFrom2;
    @UiField
    LineColInput lineColFrom2;
    @UiField
    Label lblCharCountSpinner1;
    @UiField
    ValueSpinner charCountSpinner1;

    @UiField(provided = true)
    RadioButton radioOffsetToOffset;
    @UiField
    Label lblCharOffsetFromSpinner1;
    @UiField
    ValueSpinner charOffsetFromSpinner1; // one based
    @UiField
    Label lblCharOffsetToSpinner1;
    @UiField
    ValueSpinner charOffsetToSpinner1; // one based

    @UiField(provided = true)
    RadioButton radioOffsetWithCount;
    @UiField
    Label lblCharOffsetFromSpinner2;
    @UiField
    ValueSpinner charOffsetFromSpinner2; // one based
    @UiField
    Label lblCharCountSpinner2;
    @UiField
    ValueSpinner charCountSpinner2;

    private RowCount<Long> totalCharCount = RowCount.of(0L, false);

    @Inject
    public CharacterRangeSelectionViewImpl(final EventBus eventBus,
                                           final Binder binder) {

        radioLocationToLocation = new RadioButton(RADIO_BUTTON_GROUP);
        radioLocationWithCount = new RadioButton(RADIO_BUTTON_GROUP);
        radioOffsetToOffset = new RadioButton(RADIO_BUTTON_GROUP);
        radioOffsetWithCount = new RadioButton(RADIO_BUTTON_GROUP);

        widget = binder.createAndBindUi(this);

        setInitialMinMax();

        radioLocationToLocation.addValueChangeHandler(event -> updateRadioGroup());
        radioLocationWithCount.addValueChangeHandler(event -> updateRadioGroup());
        radioOffsetToOffset.addValueChangeHandler(event -> updateRadioGroup());
        radioOffsetWithCount.addValueChangeHandler(event -> updateRadioGroup());
        updateRadioGroup();
    }

    private void updateRadioGroup() {
        lineColFrom1.setEnabled(radioLocationToLocation.getValue());
        lblLineColFrom1.setStyleDependentName(
                "disabled", !radioLocationToLocation.getValue());
        lineColTo.setEnabled(radioLocationToLocation.getValue());
        lblLineColTo.setStyleDependentName(
                "disabled", !radioLocationToLocation.getValue());

        lineColFrom2.setEnabled(radioLocationWithCount.getValue());
        lblLineColFrom2.setStyleDependentName(
                "disabled", !radioLocationWithCount.getValue());
        charCountSpinner1.setEnabled(radioLocationWithCount.getValue());
        lblCharCountSpinner1.setStyleDependentName(
                "disabled", !radioLocationWithCount.getValue());

        charOffsetFromSpinner1.setEnabled(radioOffsetToOffset.getValue());
        lblCharOffsetFromSpinner1.setStyleDependentName(
                "disabled", !radioOffsetToOffset.getValue());
        charOffsetToSpinner1.setEnabled(radioOffsetToOffset.getValue());
        lblCharOffsetToSpinner1.setStyleDependentName(
                "disabled", !radioOffsetToOffset.getValue());

        charOffsetFromSpinner2.setEnabled(radioOffsetWithCount.getValue());
        lblCharOffsetFromSpinner2.setStyleDependentName(
                "disabled", !radioOffsetWithCount.getValue());
        charCountSpinner2.setEnabled(radioOffsetWithCount.getValue());
        lblCharCountSpinner2.setStyleDependentName(
                "disabled", !radioOffsetWithCount.getValue()); }

    private void setInitialMinMax() {
        charCountSpinner1.setMin(1);
        charCountSpinner1.setMax(Long.MAX_VALUE);

        charCountSpinner2.setMin(1);
        charCountSpinner2.setMax(Long.MAX_VALUE);

        charOffsetFromSpinner1.setMin(1);
        charOffsetFromSpinner1.setMax(Long.MAX_VALUE);

        charOffsetFromSpinner2.setMin(1);
        charOffsetFromSpinner2.setMax(Long.MAX_VALUE);

        charOffsetToSpinner1.setMin(1);
        charOffsetToSpinner1.setMax(Long.MAX_VALUE);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public DataRange getDataRange() {
        final DataRange.Builder dataRangeBuilder = DataRange.builder();

        if (radioLocationToLocation.getValue()) {
            lineColFrom1.getLocation().ifPresent(dataRangeBuilder::fromLocation);
            lineColTo.getLocation().ifPresent(dataRangeBuilder::toLocation);
        } else if (radioLocationWithCount.getValue()) {
            lineColFrom2.getLocation().ifPresent(dataRangeBuilder::fromLocation);
            dataRangeBuilder.withLength((long) charCountSpinner1.getValue());
        } else if (radioOffsetToOffset.getValue()) {
            dataRangeBuilder
                    .fromCharOffset(toZeroBased(charOffsetFromSpinner1.getValue()))
                    .toCharOffset((long) charOffsetToSpinner1.getValue() - ONE_TO_ZERO_BASED_DECREMENT);
        } else if (radioOffsetWithCount.getValue()) {
            dataRangeBuilder
                    .fromCharOffset(toZeroBased(charOffsetFromSpinner2.getValue()))
                    .withLength((long) charCountSpinner2.getValue());
        }

        return dataRangeBuilder.build();
    }

    @Override
    public void setDataRange(final DataRange dataRange) {
        setEnabledAndCheckedStates(dataRange);
        updateRadioGroup();

        dataRange.getOptLocationFrom().ifPresent(location -> {
            lineColFrom1.setValue(location);
            lineColFrom2.setValue(location);
        });
        dataRange.getOptCharOffsetFrom().ifPresent(offset -> {
            charOffsetFromSpinner1.setValue(toOneBased(offset));
            charOffsetFromSpinner2.setValue(toOneBased(offset));
        });
        dataRange.getOptLocationTo().ifPresent(location -> {
            lineColTo.setValue(location);
        });
        dataRange.getOptCharOffsetTo().ifPresent(offset -> {
            charOffsetToSpinner1.setValue(toOneBased(offset));
        });

        dataRange.getOptLength().ifPresent(count -> {
            charCountSpinner1.setValue(count);
            charCountSpinner2.setValue(count);
        });
    }


    private void setEnabledAndCheckedStates(final DataRange dataRange) {
        if (dataRange.getOptLocationFrom().isPresent()) {
            radioLocationToLocation.setValue(true, true);
            radioLocationWithCount.setValue(false, true);
            radioOffsetToOffset.setValue(false, true);
            radioOffsetWithCount.setValue(false, true);
        } else {
            radioLocationToLocation.setValue(false, true);
            radioLocationWithCount.setValue(false, true);
            radioOffsetToOffset.setValue(false, true);
            radioOffsetWithCount.setValue(true, true);
        }
    }

    private String getCountText(final RowCount<Long> count) {
        return "of " + (count.isExact()
                ? count.getCount()
                : "?");
    }

    private void updateCountLabels() {
        lblTotalCharCount.setText(totalCharCount.isExact()
                ? totalCharCount.getCount().toString()
                : "?");
    }

    @Override
    public void setTotalCharsCount(final RowCount<Long> totalCharCount) {
        this.totalCharCount = totalCharCount;
        if (totalCharCount.isExact() && totalCharCount.getCount() != null) {
            charOffsetFromSpinner1.setMax(totalCharCount.getCount());
            charOffsetFromSpinner2.setMax(totalCharCount.getCount());
            charOffsetToSpinner1.setMax(totalCharCount.getCount());
            charCountSpinner1.setMax(totalCharCount.getCount());
            charCountSpinner2.setMax(totalCharCount.getCount());
        } else {
            charOffsetFromSpinner1.setMax(Long.MAX_VALUE);
            charOffsetFromSpinner2.setMax(Long.MAX_VALUE);
            charOffsetToSpinner1.setMax(Long.MAX_VALUE);
            charCountSpinner1.setMax(Long.MAX_VALUE);
            charCountSpinner2.setMax(Long.MAX_VALUE);
        }
        updateCountLabels();
    }

    private long toOneBased(final long zeroBasedValue) {
        return zeroBasedValue + 1;
    }

    private long toOneBased(final int zeroBasedValue) {
        return zeroBasedValue + 1;
    }

    private long toZeroBased(final long oneBasedValue) {
        return oneBasedValue - 1;
    }

    public interface Binder extends UiBinder<Widget, CharacterRangeSelectionViewImpl> {
    }
}
