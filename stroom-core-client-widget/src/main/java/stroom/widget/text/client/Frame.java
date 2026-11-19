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

package stroom.widget.text.client;

/// An inline frame (an `iframe`), Stroom's version of GWT's `Frame`: its class is `frame`
/// (`frame.css`, which draws no border), not GWT's `gwt-Frame`, whose bevelled border Stroom's frames
/// all turned off.
public class Frame extends com.google.gwt.user.client.ui.Frame {

    private static final String BLOCK = "frame";

    /// Makes a frame showing nothing.
    public Frame() {
        setStyleName(BLOCK);
    }

    /// @param url The address to show.
    public Frame(final String url) {
        this();
        setUrl(url);
    }
}
