package stroom.data.client.view;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ProcessChoicePresenter;
import stroom.preferences.client.UserPreferencesManager;
import stroom.widget.customdatebox.client.MyDateBox;
import stroom.widget.tickbox.client.view.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class ProcessChoiceViewImpl extends ViewImpl implements ProcessChoicePresenter.ProcessChoiceView {

    @UiField
    ValueSpinner priority;
    @UiField
    TickBox autoPriority;
    @UiField
    TickBox reprocess;
    @UiField
    TickBox enabled;
    @UiField(provided = true)
    MyDateBox minMetaCreateTimeMs;
    @UiField(provided = true)
    MyDateBox maxMetaCreateTimeMs;

    private final Widget widget;

    @Inject
    public ProcessChoiceViewImpl(final Binder binder,
                                 final UserPreferencesManager userPreferencesManager) {
        minMetaCreateTimeMs = new MyDateBox(userPreferencesManager.isUtc());
        maxMetaCreateTimeMs = new MyDateBox(userPreferencesManager.isUtc());
        widget = binder.createAndBindUi(this);

        priority.setMax(100);
        priority.setMin(1);
        priority.setValue(10);

        autoPriority.setBooleanValue(true);

        enabled.setBooleanValue(true);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public int getPriority() {
        return priority.getValue();
    }

    @Override
    public boolean isAutoPriority() {
        return autoPriority.getBooleanValue();
    }

    @Override
    public boolean isReprocess() {
        return reprocess.getBooleanValue();
    }

    @Override
    public boolean isEnabled() {
        return enabled.getBooleanValue();
    }

    @Override
    public Long getMinMetaCreateTimeMs() {
        return minMetaCreateTimeMs.getMilliseconds();
    }

    @Override
    public void setMinMetaCreateTimeMs(final Long minMetaCreateTimeMs) {
        this.minMetaCreateTimeMs.setMilliseconds(minMetaCreateTimeMs);
    }

    @Override
    public Long getMaxMetaCreateTimeMs() {
        return maxMetaCreateTimeMs.getMilliseconds();
    }

    @Override
    public void setMaxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
        this.maxMetaCreateTimeMs.setMilliseconds(maxMetaCreateTimeMs);
    }

    @UiHandler("reprocess")
    public void onChange(final ValueChangeEvent<TickBoxState> event) {
        setMaxMetaCreateTimeMs(System.currentTimeMillis());
    }

    public interface Binder extends UiBinder<Widget, ProcessChoiceViewImpl> {

    }
}
