/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.feed.client;

import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.feed.client.OpenFeedEvent.Handler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;
import java.util.Optional;

public class OpenFeedEvent extends GwtEvent<Handler> {

    private static Type<Handler> TYPE;
    private final String name;
    private final boolean forceOpen;
    private final boolean fullScreen;
    private final CommonDocLinkTab selectedTab;

    private OpenFeedEvent(final String name,
                          final boolean forceOpen,
                          final boolean fullScreen,
                          final CommonDocLinkTab selectedTab) {
        this.name = name;
        this.forceOpen = forceOpen;
        this.fullScreen = fullScreen;
        this.selectedTab = selectedTab;
    }

    public static void fire(final HasHandlers handlers,
                            final String name,
                            final boolean forceOpen) {
        handlers.fireEvent(new OpenFeedEvent(name, forceOpen, false, null));
    }

    public static void fire(final HasHandlers handlers,
                            final String name,
                            final boolean forceOpen,
                            final boolean fullScreen) {
        handlers.fireEvent(new OpenFeedEvent(name, forceOpen, fullScreen, null));
    }

    public static void fire(final HasHandlers handlers,
                            final String name,
                            final boolean forceOpen,
                            final boolean fullScreen,
                            final CommonDocLinkTab selectedTab) {
        handlers.fireEvent(new OpenFeedEvent(name, forceOpen, fullScreen, selectedTab));
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

    public String getName() {
        return name;
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

    public static Builder builder(final HasHandlers handlers, final String name) {
        return new Builder(handlers, name);
    }

    // --------------------------------------------------------------------------------

    public static final class Builder {

        private final HasHandlers hasHandlers;
        private final String name;
        private boolean forceOpen = true;
        private boolean fullScreen = false;
        private CommonDocLinkTab selectedTab = null;

        private Builder(final HasHandlers hasHandlers, final String name) {
            this.hasHandlers = Objects.requireNonNull(hasHandlers);
            this.name = Objects.requireNonNull(name);
        }

        public Builder forceOpen(final boolean forceOpen) {
            this.forceOpen = forceOpen;
            return this;
        }

        public Builder fullScreen(final boolean fullScreen) {
            this.fullScreen = fullScreen;
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
            hasHandlers.fireEvent(new OpenFeedEvent(name, forceOpen, fullScreen, selectedTab));
        }
    }

    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onOpen(final OpenFeedEvent event);
    }
}
