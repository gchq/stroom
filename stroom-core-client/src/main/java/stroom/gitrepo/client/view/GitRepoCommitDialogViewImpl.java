package stroom.gitrepo.client.view;

import stroom.gitrepo.client.presenter.GitRepoCommitDialogPresenter;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.UiHandlers;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import javax.inject.Inject;

public class GitRepoCommitDialogViewImpl
        extends ViewWithUiHandlers<GitRepoCommitDialogViewImpl.GitRepoCommitDialogUiHandlers>
        implements GitRepoCommitDialogPresenter.GitRepoCommitDialogView {

    /** The widget that this represents */
    private final Widget widget;

    /** Where the user types the commit message */
    @UiField
    TextArea txtCommitMessage;

    /**
     * Injected constructor.
     */
    @Inject
    public GitRepoCommitDialogViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    /**
     * @return The underlying widget.
     */
    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getCommitMessage() {
        return txtCommitMessage.getText();
    }

    @Override
    public void resetData() {
        txtCommitMessage.setText("");
    }

    /**
     * Interface to keep GWT UiBinder happy
     */
    public interface Binder extends UiBinder<Widget, GitRepoCommitDialogViewImpl> {
        // No code
    }

    public interface GitRepoCommitDialogUiHandlers extends UiHandlers {
        // No code
    }

}
