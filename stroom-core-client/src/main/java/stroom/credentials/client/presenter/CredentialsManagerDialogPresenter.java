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

package stroom.credentials.client.presenter;

import stroom.entity.client.presenter.MarkdownConverter;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

/**
 * Dialog to be called from clients when they want to pick an available credential.
 */
public class CredentialsManagerDialogPresenter
        extends MyPresenterWidget<CredentialsManagerDialogPresenter.CredentialsManagerDialogView> {

    /** Converts markdown to HTML */
    private final MarkdownConverter markdownConverter;

    /** Width of dialog */
    private static final int DIALOG_WIDTH = 700;

    /** Height of dialog */
    private static final int DIALOG_HEIGHT = 700;

    /**
     * Injected constructor.
     */
    @Inject
    public CredentialsManagerDialogPresenter(final EventBus eventBus,
                                             final CredentialsManagerDialogView view,
                                             final MarkdownConverter markdownConverter,
                                             final CredentialsListPresenter credentialsListPresenter) {
        super(eventBus, view);
        this.markdownConverter = markdownConverter;
        credentialsListPresenter.setParentPresenter(this);
        credentialsListPresenter.setDefaultSelection(false);
        view.setCredentialsList(credentialsListPresenter);
        this.setInSlot(CredentialsListPresenter.CREDENTIALS_LIST, credentialsListPresenter);
    }

    /**
     * Call to prepare the dialog to be shown.
     * @param builder The builder for the dialog.
     * @param markdownLabel Any HTML information we want to give the user about
     *              the credentials; for example where they can get the
     *              credentials from. null if not required.
     * @param credentialsId The UUID of the currently selected credentials. Can be null
     *                      if nothing is selected.
     */
    public void setupDialog(final ShowPopupEvent.Builder builder,
                            final String markdownLabel,
                            final String credentialsId) {

        final SafeHtml html = markdownConverter.convertMarkdownToHtml(markdownLabel);
        this.getView().setLabelHtml(html);

        builder.popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Credentials")
                .modal(true);
        this.getView().setCredentialsId(credentialsId);
    }

    /**
     * @return The selected UUID, or null if nothing is selected.
     */
    public String getCredentialsId() {
        return this.getView().getCredentialsId();
    }

    /**
     * Interface for GWT.
     */
    public interface CredentialsManagerDialogView extends View {

        /**
         * Hooks up the UI
         */
        void setCredentialsList(CredentialsListPresenter credentialsList);

        /**
         * Sets the label that tells the user where to get credentials from,
         * if necessary. If null then no label shown.
         */
        void setLabelHtml(SafeHtml html);

        /**
         * @param uuid The UUID that is currently selected.
         */
        void setCredentialsId(String uuid);

        /**
         * @return The selected UUID.
         */
        String getCredentialsId();

    }
}
