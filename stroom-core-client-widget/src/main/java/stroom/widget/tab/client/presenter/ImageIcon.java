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

package stroom.widget.tab.client.presenter;

import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.ui.Image;
import stroom.svg.client.Icon;

public class ImageIcon implements Icon {
    private final Image image;

    private ImageIcon(final Image image) {
        this.image = image;
    }

    public static ImageIcon create(final Image image) {
        if (image == null) {
            return null;
        }
        return new ImageIcon(image);
    }

    public static ImageIcon create(final ImageResource imageResource) {
        if (imageResource == null) {
            return null;
        }
        return new ImageIcon(new Image(imageResource));
    }

    public static ImageIcon create(final String url) {
        if (url == null) {
            return null;
        }
        return new ImageIcon(new Image(url));
    }

    public Image getImage() {
        return image;
    }
}
