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

package stroom.widget.util.client;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.MouseEvent;

public class MouseUtil {

    public static boolean isPrimary(final MouseEvent<?> event) {
//        GWT.log("isPrimary: " + event.getNativeEvent().getType() + " " + event.getNativeButton());
        return (event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0;
    }

    public static boolean isSecondary(final MouseEvent<?> event) {
//        GWT.log("isSecondary: " + event.getNativeEvent().getType() + " " + event.getNativeButton());
        return (event.getNativeButton() & NativeEvent.BUTTON_RIGHT) != 0;
    }

    public static boolean isPrimary(final NativeEvent event) {
//        GWT.log("isPrimary: " + event.getType() + " " + event.getButton());
        return (event.getButton() & NativeEvent.BUTTON_LEFT) != 0;
    }

    public static boolean isSecondary(final NativeEvent event) {
//        GWT.log("isSecondary: " + event.getType() + " " + event.getButton());
        return (event.getButton() & NativeEvent.BUTTON_RIGHT) != 0;
    }
}
