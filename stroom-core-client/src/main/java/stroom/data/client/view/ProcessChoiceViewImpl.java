package stroom.data.client.view;

import stroom.data.client.presenter.ProcessChoicePresenter;
import stroom.preferences.client.UserPreferencesManager;
import stroom.widget.customdatebox.client.MyDateBox;
import stroom.widget.tickbox.client.view.CustomCheckBox;
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
    CustomCheckBox autoPriority;
    @UiField
    CustomCheckBox reprocess;
    @UiField
    CustomCheckBox enabled;
    @UiField
    MyDateBox minMetaCreateTimeMs;
    @UiField
    MyDateBox maxMetaCreateTimeMs;

    private final Widget widget;

    @Inject
    public ProcessChoiceViewImpl(final Binder binder,
                                 final UserPreferencesManager userPreferencesManager) {
        widget = binder.createAndBindUi(this);
        minMetaCreateTimeMs.setUtc(userPreferencesManager.isUtc());
        maxMetaCreateTimeMs.setUtc(userPreferencesManager.isUtc());

        priority.setMax(100);
        priority.setMin(1);
        priority.setValue(10);

        autoPriority.setValue(true);

        enabled.setValue(true);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        priority.focus();
    }

    @Override
    public int getPriority() {
        return priority.getValue();
    }

    @Override
    public boolean isAutoPriority() {
        return autoPriority.getValue();
    }

    @Override
    public boolean isReprocess() {
        return reprocess.getValue();
    }

    @Override
    public boolean isEnabled() {
        return enabled.getValue();
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
    public void onChange(final ValueChangeEvent<Boolean> event) {
        setMaxMetaCreateTimeMs(System.currentTimeMillis());
    }

    public interface Binder extends UiBinder<Widget, ProcessChoiceViewImpl> {

    }
}
