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

package stroom.svg.client;

import stroom.svg.shared.SvgImage;

public class Preset {

    private final SvgImage svgImage;
    private final String title;
    private final boolean enabled;

    public Preset(final SvgImage svgImage,
                  final String title,
                  final boolean enabled) {
        this.svgImage = svgImage;
        this.title = title;
        this.enabled = enabled;
    }

    public SvgImage getSvgImage() {
        return svgImage;
    }

    public String getTitle() {
        return title;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasTitle() {
        return title != null && !title.isEmpty();
    }

    public Preset title(final String title) {
        return new Preset(svgImage, title, isEnabled());
    }

    public Preset enabled(final boolean enabled) {
        return new Preset(svgImage, getTitle(), enabled);
    }

    public Preset with(final String title, final boolean enabled) {
        return new Preset(svgImage, title, enabled);
    }
}
