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

import stroom.credentials.client.presenter.CredentialEditPresenter;
import stroom.credentials.client.presenter.CredentialsListPresenter;
import stroom.credentials.client.presenter.CredentialsPresenter.CredentialsView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

/**
 * GWT View implementation for the top-level view of the App Store page.
 */
public class CredentialsViewImpl extends ViewImpl implements CredentialsView {

    /** Underlying widget */
    private final Widget widget;

    @UiField
    public SimplePanel credentialsList;

    /**
     * Injected constructor.
     * @param binder Links this to the XML UI spec.
     */
    @SuppressWarnings("unused")
    @Inject
    public CredentialsViewImpl(final Binder binder) {
        this.widget = binder.createAndBindUi(this);
    }

    /**
     * Required by GWT.
     * @return the widget.
     */
    @Override
    public Widget asWidget() {
        return widget;
    }

    /**
     * Used to add the list of credentials to the main tab.
     */
    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (CredentialsListPresenter.CREDENTIALS_LIST.equals(slot)) {
            credentialsList.setWidget(content);
        } else if (CredentialEditPresenter.CREDENTIALS_TABS.equals(slot)) {
            credentialsList.setWidget(content);
        }
    }

    /**
     * Interface used by ctor; keeps GWT happy.
     */
    public interface Binder extends UiBinder<Widget, CredentialsViewImpl> {
        // No code
    }
}
