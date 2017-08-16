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

package stroom.pipeline.structure.client.view;

import com.google.gwt.dom.client.ImageElement;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.RootPanel;

import java.util.HashMap;
import java.util.Map;

public class ImageCache {
    private static Map<String, ImageElement> imageCache = new HashMap<>();

    public static void getImage(final ImageResource imageResource, final LoadCallback callback) {
        if (imageResource == null) {
            callback.onLoad(null);

        } else {
            final SafeUri uri = imageResource.getSafeUri();
            final String url = uri.toString();

            // Try and get from cache to start with.
            final ImageElement cached = imageCache.get(url);
            if (cached != null) {
                callback.onLoad(cached);

            } else {
                final Image image = new Image(uri);

                // To render images we need to wait for the image to load before
                // it can be rendered to the canvas.
                image.addLoadHandler(event -> {
                    // Remove the image from the root panel.
                    RootPanel.get().remove(image);

                    final ImageElement imageElement = ImageElement.as(image.getElement());
                    imageCache.put(url, imageElement);

                    callback.onLoad(imageElement);
                });

                // Add the image to the root panel to get it to load.
                image.setVisible(false);
                RootPanel.get().add(image);
            }
        }
    }

    public static void getImage(final String url, final LoadCallback callback) {
        if (url == null) {
            callback.onLoad(null);

        } else {
            // Try and get from cache to start with.
            final ImageElement cached = imageCache.get(url);
            if (cached != null) {
                callback.onLoad(cached);

            } else {
                final Image image = new Image(url);

                // To render images we need to wait for the image to load before
                // it can be rendered to the canvas.
                image.addLoadHandler(event -> {
                    // Remove the image from the root panel.
                    RootPanel.get().remove(image);

                    final ImageElement imageElement = ImageElement.as(image.getElement());
                    imageCache.put(url, imageElement);

                    callback.onLoad(imageElement);
                });

                // Add the image to the root panel to get it to load.
                image.setVisible(false);
                RootPanel.get().add(image);
            }
        }
    }

    public interface LoadCallback {
        void onLoad(ImageElement imageElement);
    }
}
