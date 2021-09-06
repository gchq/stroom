package stroom.data.client.view;

import stroom.data.client.presenter.ProcessChoicePresenter;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
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

    private final Widget widget;

    @Inject
    public ProcessChoiceViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

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

    public interface Binder extends UiBinder<Widget, ProcessChoiceViewImpl> {

    }
}
