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
 */

package stroom.widget.button.client;

public class ColourSet {
    private final String enabled;
    private final String hover;
    private final String down;
    private final String disabled;

    public ColourSet(final String enabled, final String hover, final String down, final String disabled) {
        this.enabled = enabled;
        this.hover = hover;
        this.down = down;
        this.disabled = disabled;
    }

    public static ColourSet create(final String colour) {
        return new ColourSet(colour, colour, colour, "rgba(0,0,0,0.3)");
    }

    public static ColourSet create(final String enabled, final String hover) {
        return new ColourSet(enabled, hover, hover, "rgba(0,0,0,0.3)");
    }

    public String getEnabled() {
        return enabled;
    }

    public String getHover() {
        return hover;
    }

    public String getDown() {
        return down;
    }

    public String getDisabled() {
        return disabled;
    }
}
