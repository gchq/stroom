package stroom.data.client.view;

import stroom.data.client.presenter.ItemSelectionPresenter.ItemSelectionView;
import stroom.util.shared.RowCount;
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
        return toZeroBased(itemNoSpinner.getValue());
    }

    @Override
    public void setItemNo(final long itemNo) {
        itemNoSpinner.setValue(toOneBased(itemNo));
    }

    @Override
    public void setTotalItemsCount(final RowCount<Long> totalItemsCount) {
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
