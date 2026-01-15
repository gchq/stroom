package stroom.credentials.client.view;

import stroom.credentials.client.presenter.AccessTokenSecretPresenter.AccessTokenSecretView;
import stroom.document.client.event.DirtyUiHandlers;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import javax.inject.Inject;

public class AccessTokenSecretViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements AccessTokenSecretView {

    private final Widget widget;

    @UiField
    TextBox accessToken;

    @Inject
    @SuppressWarnings("unused")
    public AccessTokenSecretViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getAccessToken() {
        return accessToken.getValue();
    }

    @Override
    public void setAccessToken(final String accessToken) {
        this.accessToken.setValue(accessToken);
    }

    @Override
    public void focus() {
        accessToken.setFocus(true);
    }

    public interface Binder extends UiBinder<Widget, AccessTokenSecretViewImpl> {

    }
}
