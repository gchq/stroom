package stroom.data.client.view;

import stroom.data.client.presenter.SourceLocationPresenter.SourceLocationView;
import stroom.data.shared.DataRange;
import stroom.pipeline.shared.SourceLocation;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.RowCount;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.ViewImpl;

public class SourceLocationViewImpl extends ViewImpl implements SourceLocationView {

    private static final int ZERO_TO_ONE_BASE_INCREMENT = 1;

    private final Widget widget;

    @UiField
    Grid sourceGrid;

    @UiField
    Grid characterGrid;

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
//    @UiField
//    ValueSpinner segmentNoToField;
    @UiField
    Label segmentCountLbl;

    // From
    @UiField
    CheckBox  lineColFromCheckBox;
    @UiField
    ValueSpinner lineNoFromSpinner; // one based
    @UiField
    ValueSpinner colNoFromSpinner; // one based
    @UiField
    CheckBox  charOffsetFromCheckBox;
    @UiField
    ValueSpinner charOffsetFromSpinner; // one based
    @UiField
    Label charCountFromLbl;

    // To
    @UiField
    CheckBox  lineColToCheckBox;
    @UiField
    ValueSpinner lineNoToSpinner;
    @UiField
    ValueSpinner colNoToSpinner;
    @UiField
    CheckBox  charOffsetToCheckBox;
    @UiField
    ValueSpinner charOffsetToSpinner; // one based
    @UiField
    Label charCountToLbl;

    // Size
    @UiField
    CheckBox  charCountCheckBox;
    @UiField
    ValueSpinner charCountSpinner;

    private RowCount<Long> partCount = RowCount.of(0L, false);
    private RowCount<Long> segmentCount = RowCount.of(0L, false);
    private RowCount<Long> totalCharCount = RowCount.of(0L, false);

    @Inject
    public SourceLocationViewImpl(final EventBus eventBus,
                                  final Binder binder) {

        widget = binder.createAndBindUi(this);

        setInitialMinMax();

        idField.setEnabled(false);
        partNoField.setEnabled(false);
        segmentNoField.setEnabled(false);

        idField.setValue(1);
        partNoField.setValue(1);
        segmentNoField.setValue(1);
//        segmentNoToField.setValue(1);

        partTitleLbl.setText("Part Number:");
        segmentTitleLbl.setText("Record Number:");
        partCountLbl.setText("");
        segmentCountLbl.setText("");

        lineColFromCheckBox.addValueChangeHandler(event -> {
            lineNoFromSpinner.setEnabled(event.getValue());
            colNoFromSpinner.setEnabled(event.getValue());
            colNoFromSpinner.getTextBox().setValue("");
        });
        charOffsetFromCheckBox.addValueChangeHandler(event ->
                charOffsetFromSpinner.setEnabled(event.getValue()));

        lineColToCheckBox.addValueChangeHandler(event -> {
            lineNoToSpinner.setEnabled(event.getValue());
            colNoToSpinner.setEnabled(event.getValue());
        });

        charOffsetToCheckBox.addValueChangeHandler(event ->
                charOffsetToSpinner.setEnabled(event.getValue()));

        charCountCheckBox.addValueChangeHandler(event ->
                charCountSpinner.setEnabled(event.getValue()));
    }

    private void setInitialMinMax() {
        idField.setMin(1);
        partNoField.setMin(1);
        segmentNoField.setMin(1);
//        segmentNoToField.setMin(1);
        lineNoFromSpinner.setMin(1);
        colNoFromSpinner.setMin(1);
        lineNoToSpinner.setMin(1);
        colNoToSpinner.setMin(1);

        idField.setMax(Long.MAX_VALUE);
        partNoField.setMax(Long.MAX_VALUE);
        segmentNoField.setMax(Long.MAX_VALUE);
//        segmentNoToField.setMax(Long.MAX_VALUE);
        lineNoFromSpinner.setMax(Long.MAX_VALUE);
        colNoFromSpinner.setMax(Long.MAX_VALUE);
        lineNoToSpinner.setMax(Long.MAX_VALUE);
        colNoToSpinner.setMax(Long.MAX_VALUE);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public SourceLocation getSourceLocation() {
        DataRange.Builder dataRangeBuilder = DataRange.builder();

        if (lineColFromCheckBox.getValue()) {
            dataRangeBuilder.fromLocation(DefaultLocation.of(
                    lineNoFromSpinner.getValue(),
                    colNoFromSpinner.getValue()));
        }
        if (charOffsetFromCheckBox.getValue()) {
            dataRangeBuilder.fromCharOffset(toZeroBased(charOffsetFromSpinner.getValue()));
        }
        if (lineColToCheckBox.getValue()) {
            dataRangeBuilder.toLocation(DefaultLocation.of(
                    lineNoToSpinner.getValue(),
                    colNoToSpinner.getValue()));
        }
        if (charOffsetToCheckBox.getValue()) {
            dataRangeBuilder.toCharOffset(toZeroBased(charOffsetToSpinner.getValue()));
        }
        if (charCountCheckBox.getValue()) {
            dataRangeBuilder.withLength((long) charCountSpinner.getValue());
        }
        final SourceLocation.Builder sourceLocationBuilder = SourceLocation.builder(idField.getValue());

        if (partNoField.isEnabled()) {
            sourceLocationBuilder.withPartNo(toZeroBased(partNoField.getValue()));
        }
        if (segmentNoField.isEnabled()) {
            sourceLocationBuilder.withSegmentNumber(toZeroBased(segmentNoField.getValue()));
        }

        return sourceLocationBuilder
                .withDataRange(dataRangeBuilder.build())
                .build();
    }

    @Override
    public void setSourceLocation(final SourceLocation sourceLocation) {
        setEnabledAndCheckedStates(sourceLocation);

        idField.setValue(sourceLocation.getId());
        partNoField.setValue(toOneBased(sourceLocation.getPartNo()));
        segmentNoField.setValue(toOneBased(sourceLocation.getSegmentNo()));

        sourceLocation.getOptDataRange().ifPresent(dataRange -> {
            lineColFromCheckBox.setValue(true);
            dataRange.getOptLocationFrom().ifPresent(location -> {
                lineNoFromSpinner.setValue(location.getLineNo());
                colNoFromSpinner.setValue(location.getColNo());
                    });
            dataRange.getOptCharOffsetFrom().ifPresent(offset ->
                    charOffsetFromSpinner.setValue(toOneBased(offset)));

            dataRange.getOptLocationTo().ifPresent(location -> {
                lineNoToSpinner.setValue(location.getLineNo());
                colNoToSpinner.setValue(location.getColNo());
            });
            dataRange.getOptCharOffsetTo().ifPresent(offset ->
                    charOffsetToSpinner.setValue(toOneBased(offset)));

            dataRange.getOptLength().ifPresent(charCountSpinner::setValue);
        });
    }

    private void setEnabledAndCheckedStates(final SourceLocation sourceLocation) {
        final boolean hasLineColFrom = sourceLocation.getOptDataRange()
                .flatMap(DataRange::getOptLocationFrom)
                .isPresent();
        lineColFromCheckBox.setValue(hasLineColFrom);
        lineNoFromSpinner.setEnabled(hasLineColFrom);
        colNoFromSpinner.setEnabled(hasLineColFrom);

        final boolean hasCharOffsetFrom = sourceLocation.getOptDataRange()
                .flatMap(DataRange::getOptCharOffsetFrom)
                .isPresent();
        charOffsetFromCheckBox.setValue(hasCharOffsetFrom);
        charOffsetFromSpinner.setEnabled(hasCharOffsetFrom);

        final boolean hasLineColTo = sourceLocation.getOptDataRange()
                .flatMap(DataRange::getOptLocationTo)
                .isPresent();
        lineColToCheckBox.setValue(hasLineColTo);
        lineNoToSpinner.setEnabled(hasLineColTo);
        colNoToSpinner.setEnabled(hasLineColTo);

        final boolean hasCharOffsetTo = sourceLocation.getOptDataRange()
                .flatMap(DataRange::getOptCharOffsetTo)
                .isPresent();

        charOffsetToCheckBox.setValue(hasCharOffsetTo);
        charOffsetToSpinner.setEnabled(hasCharOffsetTo);

        final boolean hasCharCountCheckBox = sourceLocation.getOptDataRange()
                .flatMap(DataRange::getOptLength)
                .isPresent();

        charCountCheckBox.setValue(hasCharCountCheckBox);
        charCountSpinner.setEnabled(hasCharCountCheckBox);
    }

    private String getCountText(final RowCount<Long> count) {
        return "of " + (count.isExact()
                ? count.getCount()
                : "?");
    }

    private void updateCountLabels() {
        partCountLbl.setText(getCountText(partCount));
        segmentCountLbl.setText(getCountText(segmentCount));
        charCountFromLbl.setText(getCountText(totalCharCount));
        charCountToLbl.setText(getCountText(totalCharCount));
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
        characterGrid.getElement().getStyle().setDisplay(display);
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
            charOffsetFromSpinner.setMax(totalCharCount.getCount());
            charOffsetToSpinner.setMax(totalCharCount.getCount());
            charCountSpinner.setMax(totalCharCount.getCount());
        } else {
            charOffsetFromSpinner.setMax(Long.MAX_VALUE);
            charOffsetToSpinner.setMax(Long.MAX_VALUE);
            charCountSpinner.setMax(Long.MAX_VALUE);
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
