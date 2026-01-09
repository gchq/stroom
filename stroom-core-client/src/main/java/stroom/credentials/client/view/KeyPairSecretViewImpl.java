package stroom.credentials.client.view;

import stroom.credentials.client.presenter.KeyPairSecretPresenter.KeyPairSecretView;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import javax.inject.Inject;

public class KeyPairSecretViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements KeyPairSecretView {

    private final Widget widget;

    @UiField
    PasswordTextBox passPhrase;
    @UiField
    InlineSvgButton showPassPhrase;
    @UiField
    TextArea privateKey;
    @UiField
    TextArea publicKey;

    @Inject
    @SuppressWarnings("unused")
    public KeyPairSecretViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        showPassPhrase.setSvg(SvgImage.EYE);
        showPassPhrase.setTitle("Show Pass Phrase");
        showPassPhrase.setEnabled(true);
        passPhrase.getElement().setAttribute("placeholder", "Enter Pass Phrase");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getPassPhrase() {
        return passPhrase.getValue();
    }

    @Override
    public void setPassPhrase(final String passPhrase) {
        this.passPhrase.setValue(passPhrase);
    }

    @Override
    public String getPrivateKey() {
        return privateKey.getValue();
    }

    @Override
    public void setPrivateKey(final String privateKey) {
        this.privateKey.setValue(privateKey);
    }

    @Override
    public String getPublicKey() {
        return publicKey.getValue();
    }

    @Override
    public void setPublicKey(final String publicKey) {
        this.publicKey.setValue(publicKey);
    }

    @Override
    public void focus() {
        passPhrase.setFocus(true);
    }

    @UiHandler("showPassPhrase")
    public void onShowPassword(final ClickEvent e) {
        final String type = passPhrase.getElement().getAttribute("type");
        if (type == null || "password".equals(type)) {
            passPhrase.getElement().setAttribute("type", "text");
            showPassPhrase.setSvg(SvgImage.EYE_OFF);
            showPassPhrase.setTitle("Hide Password");
        } else {
            passPhrase.getElement().setAttribute("type", "password");
            showPassPhrase.setSvg(SvgImage.EYE);
            showPassPhrase.setTitle("Show Password");
        }
    }

    public interface Binder extends UiBinder<Widget, KeyPairSecretViewImpl> {

    }
}
