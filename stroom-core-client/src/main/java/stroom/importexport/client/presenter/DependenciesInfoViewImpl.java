package stroom.importexport.client.presenter;

import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

import javax.inject.Inject;

public class DependenciesInfoViewImpl extends ViewImpl implements DependenciesInfoPresenter.DependenciesInfoView {

    private final TextArea widget;

    @Inject
    public DependenciesInfoViewImpl() {
        widget = new TextArea();
        widget.setReadOnly(true);
        widget.setStyleName("info-layout");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setInfo(final String string) {
        widget.setText(string);
    }
}
