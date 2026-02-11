package stroom.credentials.client.view;

import stroom.credentials.client.presenter.UsernamePasswordSecretPresenter.UsernamePasswordSecretView;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import javax.inject.Inject;

public class UsernamePasswordSecretViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements UsernamePasswordSecretView {

    private final Widget widget;

    @UiField
    TextBox username;
    @UiField
    PasswordTextBox password;
    @UiField
    InlineSvgButton showPassword;

    @Inject
    @SuppressWarnings("unused")
    public UsernamePasswordSecretViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        showPassword.setSvg(SvgImage.EYE);
        showPassword.setTitle("Show Password");
        showPassword.setEnabled(true);
        password.getElement().setAttribute("placeholder", "Enter Password");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getUsername() {
        return username.getValue();
    }

    @Override
    public void setUsername(final String username) {
        this.username.setValue(username);
    }

    @Override
    public String getPassword() {
        return password.getValue();
    }

    @Override
    public void setPassword(final String password) {
        this.password.setValue(password);
    }

    @Override
    public void focus() {
        username.setFocus(true);
    }

    @UiHandler("showPassword")
    public void onShowPassword(final ClickEvent e) {
        final String type = password.getElement().getAttribute("type");
        if (type == null || "password".equals(type)) {
            password.getElement().setAttribute("type", "text");
            showPassword.setSvg(SvgImage.EYE_OFF);
            showPassword.setTitle("Hide Password");
        } else {
            password.getElement().setAttribute("type", "password");
            showPassword.setSvg(SvgImage.EYE);
            showPassword.setTitle("Show Password");
        }
    }

    public interface Binder extends UiBinder<Widget, UsernamePasswordSecretViewImpl> {

    }
}
