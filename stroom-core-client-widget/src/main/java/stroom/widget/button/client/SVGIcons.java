/*
 * Copyright 2016 Crown Copyright
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

package stroom.widget.button.client;

public final class SVGIcons {
    public static final SVGIcon SAVE = new SVGIcon("/images/save.svg", "Save", false);
    public static final SVGIcon SAVE_AS = new SVGIcon("/images/saveas.svg", "Save As", true);

    private SVGIcons() {
        // Utility class.
    }
}
