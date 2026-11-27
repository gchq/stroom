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

package stroom.util.client;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Timer;

/// Shows, for a moment, that what was clicked has been copied: the element is given [#COPIED_CLASS], whose style
/// says "Copied" beside it (rendered markdown's code spans and copy buttons).
public final class CopiedFlash {

    /// The class an element has while it shows it was copied.
    public static final String COPIED_CLASS = "markdown-copied";
    // How long it shows it.
    private static final int SHOWN_MS = 1_200;

    private CopiedFlash() {
        // Static only.
    }

    /// @param element What was clicked to copy, or null.
    public static void show(final Element element) {
        if (element == null) {
            return;
        }
        element.addClassName(COPIED_CLASS);
        new Timer() {
            @Override
            public void run() {
                element.removeClassName(COPIED_CLASS);
            }
        }.schedule(SHOWN_MS);
    }
}
