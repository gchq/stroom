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

package stroom.svg.client;

public class SvgPreset extends SvgIcon {
    private final String title;
    private final boolean enabled;

    public SvgPreset(final String url, final String title, final boolean enabled) {
        this(url, 16, 16, title, enabled);
    }

    public SvgPreset(final String url, final int width, final int height, final String title, final boolean enabled) {
        super(url, width, height);
        this.title = title;
        this.enabled = enabled;
    }

    public String getTitle() {
        return title;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
