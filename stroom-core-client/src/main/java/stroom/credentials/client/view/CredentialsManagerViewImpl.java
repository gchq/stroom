/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.credentials.client.view;

import stroom.credentials.client.presenter.CredentialsListPresenter;
import stroom.credentials.client.presenter.CredentialsManagerDialogPresenter.CredentialsManagerDialogView;
import stroom.credentials.client.presenter.CredentialsManagerDialogUiHandlers;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

/**
 * Provides a dialog box holding the Credentials Manager UI.
 * Useful for objects that want to use credentials.
 */
public class CredentialsManagerViewImpl
        extends ViewWithUiHandlers<CredentialsManagerDialogUiHandlers>
        implements CredentialsManagerDialogView {

    /** GWT widget */
    private final Widget widget;

    /** Reference to the credentials list */
    private CredentialsListPresenter credentialsList;

    /** Introduces the credentials and tells users where to get the credentials from */
    @UiField
    HTML lblHtml;
    @UiField
    SimplePanel pnlCredentialsList;

    /**
     * Injected constructor.
     */
    @Inject
    @SuppressWarnings("unused")
    public CredentialsManagerViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    /**
     * Called from the Presenter to hook up the UI.
     */
    @Override
    public void setCredentialsList(final CredentialsListPresenter credentialsListPresenter) {
        credentialsList = credentialsListPresenter;
    }

    /**
     * Used to add the list of credentials to the main tab.
     */
    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (CredentialsListPresenter.CREDENTIALS_LIST.equals(slot)) {
            pnlCredentialsList.setWidget(content);
        }
    }

    /**
     * @return The underlying widget.
     */
    @Override
    public Widget asWidget() {
        return widget;
    }

    /**
     * @param uuid The UUID that is currently selected.
     */
    @Override
    public void setCredentialName(final String uuid) {
        credentialsList.setSelectedCredentialsId(uuid);
    }

    /**
     * Allows the client to show additional information to the user.
     * @param html The HTML to show above the rest of the info.
     *             If null then the label is hidden.
     */
    @Override
    public void setLabelHtml(final SafeHtml html) {
        if (html != null) {
            lblHtml.setHTML(html);
            lblHtml.setVisible(true);
        } else {
            lblHtml.setText("");
            lblHtml.setVisible(false);
        }
    }

    /**
     * @return The UUID of the credentials set within this UI, or null if no credentials were set.
     */
    @Override
    public String getCredentialName() {
        return credentialsList.getSelectedCredentialsId();
    }

    /**
     * Interface to keep GWT UiBinder happy.
     */
    public interface Binder extends UiBinder<Widget, CredentialsManagerViewImpl> {
        // No code
    }
}
