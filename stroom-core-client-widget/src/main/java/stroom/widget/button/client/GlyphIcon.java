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

import stroom.widget.tab.client.presenter.Icon;

public class GlyphIcon implements Icon {
    private final String glyph;
    private final ColourSet colourSet;
    private final String title;
    private final boolean enabled;

    public GlyphIcon(final String glyph, final ColourSet colourSet, final String title, final boolean enabled) {
        this.glyph = glyph;
        this.colourSet = colourSet;
        this.title = title;
        this.enabled = enabled;
    }

    public String getGlyph() {
        return glyph;
    }

    public ColourSet getColourSet() {
        return colourSet;
    }

    public String getTitle() {
        return title;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
