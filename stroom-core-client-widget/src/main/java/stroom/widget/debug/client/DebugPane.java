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

package stroom.widget.debug.client;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.RootPanel;

public class DebugPane {

    private static volatile Element debug = null;

    public static void debug(final String html) {
        if (debug == null) {
            synchronized (DebugPane.class) {
                if (debug == null) {
                    debug = DOM.createDiv();
                    debug.addClassName("stroom-control");
                    debug.getStyle().setProperty("position", "absolute");
                    debug.getStyle().setProperty("bottom", "0px");
                    debug.getStyle().setProperty("left", "0px");
                    debug.getStyle().setProperty("width", "300px");
                    debug.getStyle().setProperty("height", "50px");
                    debug.getStyle().setProperty("margin", "0px");
                    debug.getStyle().setProperty("padding", "0px");
                    debug.getStyle().setProperty("border", "2px");
                    debug.getStyle().setProperty("borderStyle", "solid");
                    debug.getStyle().setProperty("borderColor", "red");
                    debug.getStyle().setProperty("zIndex", "200");

                    // We need to set the background color or mouse events will
                    // go right
                    // through the glassElem. If the SplitPanel contains an
                    // iframe, the
                    // iframe will capture the event and the slider will stop
                    // moving.


                    RootPanel.getBodyElement().appendChild(debug);
                }
            }
        }

        debug.setInnerHTML(html);
    }
}
