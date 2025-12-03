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

package stroom.document.client.event;

import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent.Handler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class OpenDocumentEvent extends GwtEvent<Handler> {

    private static Type<Handler> TYPE;
    private final DocRef docRef;
    private final boolean forceOpen;
    private final boolean fullScreen;
    private final CommonDocLinkTab selectedTab;
    private final Consumer<MyPresenterWidget<?>> callbackOnOpen;

    private OpenDocumentEvent(final DocRef docRef,
                              final boolean forceOpen,
                              final boolean fullScreen,
                              final CommonDocLinkTab selectedTab,
                              final Consumer<MyPresenterWidget<?>> callbackOnOpen) {
        this.docRef = docRef;
        this.forceOpen = forceOpen;
        this.fullScreen = fullScreen;
        this.selectedTab = selectedTab;
        this.callbackOnOpen = callbackOnOpen;
    }

    public static void fire(final HasHandlers handlers,
                            final DocRef docRef,
                            final boolean forceOpen) {
        handlers.fireEvent(new OpenDocumentEvent(docRef, forceOpen, false, null, null));
    }

    public static void fire(final HasHandlers handlers,
                            final DocRef docRef,
                            final boolean forceOpen,
                            final boolean fullScreen) {
        handlers.fireEvent(new OpenDocumentEvent(docRef, forceOpen, fullScreen, null, null));
    }

    public static void fire(final HasHandlers handlers,
                            final DocRef docRef,
                            final boolean forceOpen,
                            final boolean fullScreen,
                            final CommonDocLinkTab selectedTab) {
        handlers.fireEvent(new OpenDocumentEvent(docRef, forceOpen, fullScreen, selectedTab, null));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public final Type<Handler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onOpen(this);
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public boolean isForceOpen() {
        return forceOpen;
    }

    public boolean isFullScreen() {
        return fullScreen;
    }

    public Optional<CommonDocLinkTab> getSelectedTab() {
        return Optional.ofNullable(selectedTab);
    }

    public Consumer<MyPresenterWidget<?>> getCallbackOnOpen() {
        return callbackOnOpen;
    }

    public static Builder builder(final HasHandlers handlers, final DocRef docRef) {
        return new Builder(handlers, docRef);
    }


    // --------------------------------------------------------------------------------


    /**
     * A link/sub tab that is common to all document presenters
     */
    public enum CommonDocLinkTab {
        /**
         * The link/sub tab that the presenter deems to be its default tab.
         */
        DEFAULT,
        /**
         * The Documentation tab
         */
        DOCUMENTATION,
        /**
         * The Permissions tab
         */
        PERMISSIONS
    }


    // --------------------------------------------------------------------------------

    public static final class Builder {

        private final HasHandlers hasHandlers;
        private final DocRef docRef;
        private boolean forceOpen = true;
        private boolean fullScreen = false;
        private CommonDocLinkTab selectedTab = null;
        private Consumer<MyPresenterWidget<?>> callbackOnOpen;

        private Builder(final HasHandlers hasHandlers, final DocRef docRef) {
            this.hasHandlers = Objects.requireNonNull(hasHandlers);
            this.docRef = Objects.requireNonNull(docRef);
        }

        public Builder forceOpen(final boolean forceOpen) {
            this.forceOpen = forceOpen;
            return this;
        }

        public Builder fullScreen(final boolean fullScreen) {
            this.fullScreen = fullScreen;
            return this;
        }

        public Builder callbackOnOpen(final Consumer<MyPresenterWidget<?>> callbackOnOpen) {
            this.callbackOnOpen = callbackOnOpen;
            return this;
        }

        /**
         * The sub/link tab to select on opening
         */
        public Builder selectedTab(final CommonDocLinkTab selectedTab) {
            this.selectedTab = selectedTab;
            return this;
        }

        public void fire() {
            hasHandlers.fireEvent(new OpenDocumentEvent(docRef, forceOpen, fullScreen, selectedTab, callbackOnOpen));
        }
    }

    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onOpen(final OpenDocumentEvent event);
    }
}
