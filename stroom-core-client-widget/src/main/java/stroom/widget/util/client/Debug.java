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

import com.google.gwt.core.client.GWT;

public class Debug {

    private static boolean isEnabled_ = false;

    public static void enable() {
        isEnabled_ = true;
    }

    public static void setEnabled(final boolean isEnabled) {
        isEnabled_ = isEnabled;
    }

    public static void log(final String s) {
        GWT.log(s);
        if (isEnabled_) {
            nativeConsoleLog(s);
        }
    }

    public static void log(final String s, final Throwable e) {
        GWT.log(s, e);
        if (isEnabled_) {
            nativeConsoleLog(s);
        }
    }

    private static native void nativeConsoleLog(String s)
        /*-{ console.log( s ); }-*/;
}
