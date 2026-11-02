/*
 * Copyright 2026 Crown Copyright
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

package stroom.data.client.event;

import stroom.data.client.event.AskStroomAiBadgeEvent.Handler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

/**
 * Says how many of the user's 'Ask Stroom AI' chats need them, are working, or have unread messages, so the button
 * that shows the chat can carry a badge.
 */
public class AskStroomAiBadgeEvent extends GwtEvent<Handler> {

    private static Type<Handler> TYPE;

    private final int needsYou;
    private final int busy;
    private final int unread;

    private AskStroomAiBadgeEvent(final int needsYou, final int busy, final int unread) {
        this.needsYou = needsYou;
        this.busy = busy;
        this.unread = unread;
    }

    public static void fire(final HasHandlers source, final int needsYou, final int busy, final int unread) {
        source.fireEvent(new AskStroomAiBadgeEvent(needsYou, busy, unread));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public final Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onBadge(this);
    }

    /**
     * @return How many chats are waiting for the user, e.g. to approve something.
     */
    public int getNeedsYou() {
        return needsYou;
    }

    /**
     * @return How many chats have a run working or queued.
     */
    public int getBusy() {
        return busy;
    }

    /**
     * @return How many chats have messages the user has not seen.
     */
    public int getUnread() {
        return unread;
    }

    public interface Handler extends EventHandler {

        void onBadge(AskStroomAiBadgeEvent event);
    }
}
