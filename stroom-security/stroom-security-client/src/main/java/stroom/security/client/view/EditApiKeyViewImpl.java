package stroom.security.client.view;

import stroom.preferences.client.UserPreferencesManager;
import stroom.security.client.presenter.EditApiKeyPresenter.EditApiKeyView;
import stroom.security.client.presenter.EditApiKeyPresenter.Mode;
import stroom.util.shared.UserName;
import stroom.widget.customdatebox.client.MyDateBox;
import stroom.widget.popup.client.view.HideRequestUiHandlers;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class EditApiKeyViewImpl
        extends ViewWithUiHandlers<HideRequestUiHandlers>
        implements EditApiKeyView {

    private final Widget widget;
    private Mode mode;
    private UserName owner = null;

    @UiField
    Label ownerLabel;
    @UiField
    TextBox nameTextBox;
    @UiField
    TextBox commentsTextBox;
    @UiField
    MyDateBox expiresOnDateBox;
    @UiField
    CustomCheckBox enabledCheckBox;

    @UiField
    Label prefixLabel;
    @UiField
    TextArea apiKeyTextArea;

    @Inject
    public EditApiKeyViewImpl(final Binder binder,
                              final UserPreferencesManager userPreferencesManager) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(event -> focus());

        apiKeyTextArea.setEnabled(false);
        expiresOnDateBox.setUtc(userPreferencesManager.isUtc());
    }

    @Override
    public void setMode(final Mode mode) {
        this.mode = mode;
        if (Mode.PRE_CREATE.equals(mode)) {
            prefixLabel.setVisible(false);
            apiKeyTextArea.setVisible(false);
        } else if (Mode.POST_CREATE.equals(mode)) {
            prefixLabel.setVisible(true);
            apiKeyTextArea.setVisible(true);
        } else {
            prefixLabel.setVisible(true);
            apiKeyTextArea.setVisible(false);
        }
    }

    @Override
    public void setOwner(final UserName owner) {
        this.owner = owner;
        this.ownerLabel.setText(owner.getUserIdentityForAudit());
    }

    @Override
    public UserName getOwner(final UserName owner) {
        return owner;
    }

    @Override
    public void setName(final String name) {
        nameTextBox.setText(name);
    }

    @Override
    public String getName() {
        return nameTextBox.getText();
    }

    @Override
    public void setComments(final String comments) {
        commentsTextBox.setText(comments);
    }

    @Override
    public String getComments() {
        return commentsTextBox.getText();
    }

    @Override
    public void setExpiresOn(final Long expiresOn) {
        expiresOnDateBox.setMilliseconds(expiresOn);
    }

    @Override
    public Long getExpiresOnMs() {
        return expiresOnDateBox.getMilliseconds();
    }

    @Override
    public void setEnabled(final boolean isEnabled) {
        enabledCheckBox.setEnabled(isEnabled);
    }

    @Override
    public boolean isEnabled() {
        return enabledCheckBox.isEnabled();
    }

    @Override
    public void focus() {
        Scheduler.get().scheduleDeferred(() -> {
            nameTextBox.setFocus(true);
        });
    }

    @Override
    public void clear() {
        nameTextBox.setText("");
        commentsTextBox.setText("");
        ownerLabel.setText("");
        owner = null;
        expiresOnDateBox.setMilliseconds();
        enabledCheckBox.setEnabled(true);
        prefixLabel.setText("");
        apiKeyTextArea.setText("");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, EditApiKeyViewImpl> {

    }
}
