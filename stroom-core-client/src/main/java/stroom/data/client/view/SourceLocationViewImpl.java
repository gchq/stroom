package stroom.data.client.view;

import stroom.data.client.presenter.SourceLocationPresenter.SourceLocationView;
import stroom.data.shared.DataRange;
import stroom.pipeline.shared.SourceLocation;
import stroom.util.shared.RowCount;
import stroom.widget.linecolinput.client.LineColInput;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.ViewImpl;

public class SourceLocationViewImpl extends ViewImpl implements SourceLocationView {

    private static final String RADIO_BUTTON_GROUP = "RadioBtnGrp";

    private final Widget widget;

    // TODO @AT Do we want the meta id in here?
    @UiField
    ValueSpinner idField;
    @UiField
    Label partTitleLbl;
    @UiField
    ValueSpinner partNoField;
    @UiField
    Label partCountLbl;
    @UiField
    Label segmentTitleLbl;
    @UiField
    ValueSpinner segmentNoField;
    @UiField
    Label segmentCountLbl;
    @UiField
    Label lblTotalCharCount;

    @UiField
    Grid grid;

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

    private RowCount<Long> partCount = RowCount.of(0L, false);
    private RowCount<Long> segmentCount = RowCount.of(0L, false);
    private RowCount<Long> totalCharCount = RowCount.of(0L, false);

    @Inject
    public SourceLocationViewImpl(final EventBus eventBus,
                                  final Binder binder) {

        radioLocationToLocation = new RadioButton(RADIO_BUTTON_GROUP);
        radioLocationWithCount = new RadioButton(RADIO_BUTTON_GROUP);
        radioOffsetToOffset = new RadioButton(RADIO_BUTTON_GROUP);
        radioOffsetWithCount = new RadioButton(RADIO_BUTTON_GROUP);

        widget = binder.createAndBindUi(this);

        setInitialMinMax();

        idField.setEnabled(false);
        partNoField.setEnabled(false);
        segmentNoField.setEnabled(false);

        idField.setValue(1);
        partNoField.setValue(1);
        segmentNoField.setValue(1);

        partTitleLbl.setText("Part Number:");
        segmentTitleLbl.setText("Record Number:");
        partCountLbl.setText("");
        segmentCountLbl.setText("");

        radioLocationToLocation.addValueChangeHandler(event -> updateRadioGroup());
        radioLocationWithCount.addValueChangeHandler(event -> updateRadioGroup());
        radioOffsetToOffset.addValueChangeHandler(event -> updateRadioGroup());
        radioOffsetWithCount.addValueChangeHandler(event -> updateRadioGroup());
        updateRadioGroup();
    }

    private void updateRadioGroup() {
        lineColFrom1.setEnabled(radioLocationToLocation.getValue());
        lblLineColFrom1.setStyleDependentName("disabled", !radioLocationToLocation.getValue());
        lineColTo.setEnabled(radioLocationToLocation.getValue());
        lblLineColTo.setStyleDependentName("disabled", !radioLocationToLocation.getValue());

        lineColFrom2.setEnabled(radioLocationWithCount.getValue());
        lblLineColFrom2.setStyleDependentName("disabled", !radioLocationWithCount.getValue());
        charCountSpinner1.setEnabled(radioLocationWithCount.getValue());
        lblCharCountSpinner1.setStyleDependentName("disabled", !radioLocationWithCount.getValue());

        charOffsetFromSpinner1.setEnabled(radioOffsetToOffset.getValue());
        lblCharOffsetFromSpinner1.setStyleDependentName("disabled", !radioOffsetToOffset.getValue());
        charOffsetToSpinner1.setEnabled(radioOffsetToOffset.getValue());
        lblCharOffsetToSpinner1.setStyleDependentName("disabled", !radioOffsetToOffset.getValue());

        charOffsetFromSpinner2.setEnabled(radioOffsetWithCount.getValue());
        lblCharOffsetFromSpinner2.setStyleDependentName("disabled", !radioOffsetWithCount.getValue());
        charCountSpinner2.setEnabled(radioOffsetWithCount.getValue());
        lblCharCountSpinner2.setStyleDependentName("disabled", !radioOffsetWithCount.getValue());
   }

    private void setInitialMinMax() {
        idField.setMin(1);
        partNoField.setMin(1);
        segmentNoField.setMin(1);

        idField.setMax(Long.MAX_VALUE);
        partNoField.setMax(Long.MAX_VALUE);
        segmentNoField.setMax(Long.MAX_VALUE);

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
    public SourceLocation getSourceLocation() {
        final SourceLocation.Builder sourceLocationBuilder = SourceLocation.builder(idField.getValue());
        final DataRange.Builder dataRangeBuilder = DataRange.builder();

        if (partNoField.isEnabled()) {
            sourceLocationBuilder.withPartNo(toZeroBased(partNoField.getValue()));
        }
        if (segmentNoField.isEnabled()) {
            sourceLocationBuilder.withSegmentNumber(toZeroBased(segmentNoField.getValue()));
        }

        if (radioLocationToLocation.getValue()) {
            lineColFrom1.getLocation().ifPresent(dataRangeBuilder::fromLocation);
            lineColTo.getLocation().ifPresent(dataRangeBuilder::toLocation);
        } else if (radioLocationWithCount.getValue()) {
            lineColFrom2.getLocation().ifPresent(dataRangeBuilder::fromLocation);
            dataRangeBuilder.withLength((long) charCountSpinner1.getValue());
        } else if (radioOffsetToOffset.getValue()) {
            dataRangeBuilder
                    .fromCharOffset((long) charOffsetFromSpinner1.getValue())
                    .toCharOffset((long) charOffsetToSpinner1.getValue());
        } else if (radioOffsetWithCount.getValue()) {
            dataRangeBuilder
                    .fromCharOffset((long) charOffsetFromSpinner2.getValue())
                    .withLength((long) charCountSpinner2.getValue());
        }

        return sourceLocationBuilder
                .withDataRange(dataRangeBuilder.build())
                .build();
    }

    @Override
    public void setSourceLocation(final SourceLocation sourceLocation) {
        setEnabledAndCheckedStates(sourceLocation);
        updateRadioGroup();

        idField.setValue(sourceLocation.getId());
        partNoField.setValue(toOneBased(sourceLocation.getPartNo()));
        segmentNoField.setValue(toOneBased(sourceLocation.getSegmentNo()));

        sourceLocation.getOptDataRange().ifPresent(dataRange -> {
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
        });
    }

    private void setEnabledAndCheckedStates(final SourceLocation sourceLocation) {
        if (sourceLocation.getOptDataRange()
                .flatMap(DataRange::getOptLocationFrom)
                .isPresent()) {
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
        partCountLbl.setText(getCountText(partCount));
        segmentCountLbl.setText(getCountText(segmentCount));
        lblTotalCharCount.setText(totalCharCount.isExact()
                ? totalCharCount.getCount().toString()
                : "?");
    }

    @Override
    public void setIdEnabled(final boolean isEnabled) {
        idField.setEnabled(isEnabled);
    }

    @Override
    public void setPartNoVisible(final boolean isVisible) {
        partNoField.setEnabled(isVisible);
        partNoField.setVisible(isVisible);
        partTitleLbl.setVisible(isVisible);
        partCountLbl.setVisible(isVisible);
    }

    @Override
    public void setSegmentNoVisible(final boolean isVisible) {
        segmentNoField.setEnabled(isVisible);
        segmentNoField.setVisible(isVisible);
        segmentTitleLbl.setVisible(isVisible);
        segmentCountLbl.setVisible(isVisible);
    }

    @Override
    public void setCharacterControlsVisible(final boolean isVisible) {
        final Display display = isVisible
                ? Display.TABLE
                : Display.NONE;
        // TODO @AT Don't think we need this as it was only used for marker data which should not use
        // this screen
//        characterGrid.getElement().getStyle().setDisplay(display);
    }

    @Override
    public void setPartCount(final RowCount<Long> partCount) {
        this.partCount = partCount;
        if (partCount.isExact() && partCount.getCount() != null) {
            partNoField.setMax(partCount.getCount());
            partNoField.setEnabled(partCount.getCount() != 1);
        } else {
            partNoField.setMax(Long.MAX_VALUE);
        }
        updateCountLabels();
    }

    @Override
    public void setSegmentsCount(final RowCount<Long> segmentCount) {
        this.segmentCount = segmentCount;
        if (segmentCount.isExact() && segmentCount.getCount() != null) {
            segmentNoField.setMax(segmentCount.getCount());
            segmentNoField.setEnabled(segmentCount.getCount() != 1);
        } else {
            segmentNoField.setMax(Long.MAX_VALUE);
        }
        updateCountLabels();
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

    public interface Binder extends UiBinder<Widget, SourceLocationViewImpl> {
    }
}
