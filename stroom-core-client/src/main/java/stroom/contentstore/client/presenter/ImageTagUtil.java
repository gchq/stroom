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

package stroom.contentstore.client.presenter;

import com.google.gwt.http.client.URL;

/**
 * Provides a place for HTML image tag utilities for the Content Store.
 */
public class ImageTagUtil {

    /**
     * Returns the image tag for an icon with the given ID.
     */
    public static String getImageTag(final int height,
                                     final int width,
                                     final String id) {
        final String encodedId = URL.encode(id);
        return "<img height=\"" + height + "\" width=\"" + width
               + "\" src=\"/iconPassThrough?" + encodedId + "\">";
    }

}
