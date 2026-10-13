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

package stroom.gwt.workbench.framework.client.manager;

import stroom.gwt.workbench.framework.client.BrowserUtil;

import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.Timer;

/// Shows short notifications at the bottom of the sidebar, as React Storybook does, e.g. when a
/// story can't be opened in an editor.
public final class Notifications {

    private static final String CONTAINER_ID = "wbm-notifications";
    private static final int DURATION_MILLIS = 6000;

    private Notifications() {
        // Static utility
    }

    /// Shows a notification, which disappears after a few seconds or when closed.
    ///
    /// @param headline The bold first line.
    /// @param subHeadline The second line, may be null.
    /// @param error    True to show it as an error.
    public static void show(final String headline, final String subHeadline, final boolean error) {
        final DivElement notification = Document.get().createDivElement();
        notification.setClassName("wbm-notification" + (error
                ? " wbm-notification--error"
                : ""));
        notification.setAttribute("role", "status");
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        MenuHtml.appendIcon(builder, "wbm-icon wbm-notification__icon", error
                ? "wbm-icon-cross"
                : "wbm-icon-check");
        builder.appendHtmlConstant("<div class=\"wbm-notification__text\"><strong>")
                .appendEscaped(headline)
                .appendHtmlConstant("</strong>");
        if (subHeadline != null) {
            builder.appendHtmlConstant("<span>").appendEscaped(subHeadline).appendHtmlConstant("</span>");
        }
        builder.appendHtmlConstant("</div><button type=\"button\" class=\"wbm-icon-button wbm-notification__close\" "
                                   + "aria-label=\"Dismiss notification\">");
        MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-cross");
        builder.appendHtmlConstant("</button>");
        notification.setInnerSafeHtml(builder.toSafeHtml());
        getContainer().appendChild(notification);

        BrowserUtil.addListener(notification, "click", event -> {
            if (BrowserUtil.closest(Element.as(event.getEventTarget()), ".wbm-notification__close") != null) {
                notification.removeFromParent();
            }
        });
        new Timer() {
            @Override
            public void run() {
                notification.removeFromParent();
            }
        }.schedule(DURATION_MILLIS);
    }

    private static Element getContainer() {
        Element container = Document.get().getElementById(CONTAINER_ID);
        if (container == null) {
            container = Document.get().createDivElement();
            container.setId(CONTAINER_ID);
            container.setClassName("wbm-notifications");
            Document.get().getBody().appendChild(container);
        }
        return container;
    }
}
