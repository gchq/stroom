package stroom.credentials.client.view;

import stroom.credentials.client.presenter.SshKeySecretPresenter.SshKeySecretView;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import javax.inject.Inject;

public class SshKeySecretViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements SshKeySecretView {

    private final Widget widget;

    @UiField
    PasswordTextBox passPhrase;
    @UiField
    InlineSvgButton showPassPhrase;
    @UiField
    TextArea privateKey;
    @UiField
    CustomCheckBox verifyHosts;
    @UiField
    TextArea knownHosts;

    @Inject
    @SuppressWarnings({"unused", "checkstyle:linelength"})
    public SshKeySecretViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        showPassPhrase.setSvg(SvgImage.EYE);
        showPassPhrase.setTitle("Show Pass Phrase");
        showPassPhrase.setEnabled(true);
        passPhrase.getElement().setAttribute("placeholder", "Enter Pass Phrase");
        privateKey.getElement().setAttribute("placeholder", """
                Example:
                -----BEGIN OPENSSH PRIVATE KEY-----
                XXX
                -----END OPENSSH PRIVATE KEY-----
                """);
        verifyHosts.setValue(true);
        knownHosts.getElement().setAttribute("placeholder", """
                Example:
                github.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl
                github.com ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBEmKSENjQEezOmxkZMy7opKgwFB9nkt5YRrYMjNuG5N87uRgg6CLrbo5wAdT/y6v0mKV0U2w0WZ2YB/++Tpockg=
                github.com ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQCj7ndNxQowgcQnjshcLrqPEiiphnt+VTTvDP6mHBL9j1aNUkY4Ue1gvwnGLVlOhGeYrnZaMgRK6+PKCUXaDbC7qtbW8gIkhL7aGCsOr/C56SJMy/BCZfxd1nWzAOxSDPgVsmerOBYfNqltV9/hWCqBywINIR+5dIg6JTJ72pcEpEjcYgXkE2YEFXV1JHnsKgbLWNlhScqb2UmyRkQyytRLtL+38TGxkxCflmO+5Z8CSSNY7GidjMIZ7Q4zMjA2n1nGrlTDkzwDCsw+wqFPGQA179cnfGWOWRVruj16z6XyvxvjJwbz0wQZ75XK5tKSb7FNyeIEs4TT4jk+S4dhPeAUC5y+bDYirYgM4GC7uEnztnZyaVWQ7B381AK4Qdrwt51ZqExKbQpTUNn+EjqoTwvqNj4kqx5QUCI0ThS/YkOxJCXmPUWZbhjpCg56i+2aB6CmK2JGhn57K5mj0MNdBXA4/WnwH6XoPWJzK5Nyu2zB3nAZp+S5hpQs+p1vN1/wsjk=
                """);
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
    public boolean isVerifyHosts() {
        return verifyHosts.getValue();
    }

    @Override
    public void setVerifyHosts(final boolean verifyHosts) {
        this.verifyHosts.setValue(verifyHosts);
    }

    @Override
    public String getKnownHosts() {
        return knownHosts.getValue();
    }

    @Override
    public void setKnownHosts(final String knownHosts) {
        this.knownHosts.setValue(knownHosts);
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

    public interface Binder extends UiBinder<Widget, SshKeySecretViewImpl> {

    }
}
