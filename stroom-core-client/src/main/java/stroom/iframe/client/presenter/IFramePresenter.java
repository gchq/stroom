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

package stroom.iframe.client.presenter;

import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class IFramePresenter extends MyPresenterWidget<IFramePresenter.IFrameView>
        implements IFrameLoadUiHandlers, HasDirtyHandlers {

    @Inject
    public IFramePresenter(final EventBus eventBus, final IFrameView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    public void setId(final String id) {
        getView().setId(id);
    }

    public void setUrl(final String url) {
        getView().setUrl(url);
    }

    public void setSrcDoc(final String html) {
        getView().setSrcDoc(html);
    }

    public void setSandboxEnabled(final boolean isEnabled, final SandboxOption... sandboxOptions) {
        getView().setSandboxEnabled(isEnabled, sandboxOptions);
    }

    public void setCustomTitle(final String customTitle) {
        getView().setCustomTitle(customTitle);
    }

    public String getLabel() {
        return getView().getTitle();
    }

    public void close() {
        getView().cleanup();
    }

    @Override
    public void onTitleChange(final String title) {
        DirtyEvent.fire(this, true);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }


    // --------------------------------------------------------------------------------


    public interface IFrameView extends View, HasUiHandlers<IFrameLoadUiHandlers> {

        void setId(String id);

        void setUrl(String url);

        void setSrcDoc(final String html);

        void setSandboxEnabled(final boolean isEnabled, final SandboxOption... sandboxOptions);

        void setCustomTitle(String customTitle);

        String getTitle();

        void cleanup();
    }

    // --------------------------------------------------------------------------------

    /**
     * The possible values for the sandbox iframe attribute. Multiple options may be used.
     */
    public enum SandboxOption {
        /**
         * Allows form submission
         */
        ALLOW_FORMS("allow-forms"),
        /**
         * Allows to open modal windows
         */
        ALLOW_MODALS("allow-modals"),
        /**
         * Allows to lock the screen orientation
         */
        ALLOW_ORIENTATION_LOCK("allow-orientation-lock"),
        /**
         * Allows to use the Pointer Lock API
         */
        ALLOW_POINTER_LOCK("allow-pointer-lock"),
        /**
         * Allows popups
         */
        ALLOW_POPUPS("allow-popups"),
        /**
         * Allows popups to open new windows without inheriting the sandboxing
         */
        ALLOW_POPUPS_TO_ESCAPE_SANDBOX("allow-popups-to-escape-sandbox"),
        /**
         * Allows to start a presentation session
         */
        ALLOW_PRESENTATION("allow-presentation"),
        /**
         * Allows the iframe content to be treated as being from the same origin
         */
        ALLOW_SAME_ORIGIN("allow-same-origin"),
        /**
         * Allows to run scripts
         */
        ALLOW_SCRIPTS("allow-scripts"),
        /**
         * Allows the iframe content to navigate its top-level browsing context
         */
        ALLOW_TOP_NAVIGATION("allow-top-navigation"),
        /**
         * Allows the iframe content to navigate its top-level browsing context, but only if initiated by user
         */
        ALLOW_TOP_NAVIGATION_BY_USER_ACTIVATION("allow-top-navigation-by-user-activation");

        private final String option;

        SandboxOption(final String option) {
            this.option = option;
        }

        public String getOption() {
            return option;
        }
    }
}
