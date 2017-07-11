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

package stroom.widget.menu.client.presenter;

import com.google.gwt.user.client.Command;
import stroom.svg.client.Icon;

public class IconMenuItem extends CommandMenuItem {
    private final Icon enabledIcon;
    private final Icon disabledIcon;

    public IconMenuItem(final int priority, final Icon enabledIcon, final Icon disabledIcon,
                        final String text, final String shortcut, final boolean enabled, final Command command) {
        super(priority, text, shortcut, enabled, command);
        this.enabledIcon = enabledIcon;
        this.disabledIcon = disabledIcon;
//
//        if (enabledImage != null) {
//            this.enabledImage = new Image(enabledImage);
//        } else {
//            this.enabledImage = null;
//        }
//        if (disabledImage != null) {
//            this.disabledImage = new Image(disabledImage);
//        } else {
//            this.disabledImage = null;
//        }
    }
//
//    public IconMenuItem(final int priority, final String enabledImageUrl, final String disabledImageUrl,
//                        final String text, final String shortcut, final boolean enabled, final Command command) {
//        super(priority, text, shortcut, enabled, command);
//        if (enabledImageUrl != null) {
//            this.enabledImage = new Image(enabledImageUrl);
//        } else {
//            this.enabledImage = null;
//        }
//        if (disabledImageUrl != null) {
//            this.disabledImage = new Image(disabledImageUrl);
//        } else {
//            this.disabledImage = null;
//        }
//    }
//
//    public IconMenuItem(final int priority, final Image enabledImage, final Image disabledImage, final String text,
//                        final String shortcut, final boolean enabled, final Command command) {
//        super(priority, text, shortcut, enabled, command);
//        this.enabledImage = enabledImage;
//        this.disabledImage = disabledImage;
//    }
//
    public IconMenuItem(final int priority, final String text, final String shortcut, final boolean enabled,
                        final Command command) {
        super(priority, text, shortcut, enabled, command);
        this.enabledIcon = null;
        this.disabledIcon = null;
    }
//
//    public Image getEnabledImage() {
//        return enabledImage;
//    }
//
//    public Image getDisabledImage() {
//        return disabledImage;
//    }


    public Icon getEnabledIcon() {
        return enabledIcon;
    }

    public Icon getDisabledIcon() {
        return disabledIcon;
    }
}
