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

package stroom.cell.info.client;

import stroom.util.shared.NullSafe;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class ColourSwatchCell extends AbstractCell<String> {

    public ColourSwatchCell() {
    }

    @Override
    public void render(final Context context,
                       final String cssColour,
                       final SafeHtmlBuilder sb) {

        if (NullSafe.isBlankString(cssColour)) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            sb.appendHtmlConstant("<div class=\"colourSwatchCell colourSwatchCell-container\">");

            sb.appendHtmlConstant("<div class=\"colourSwatchCell-swatch\" style=\"background-color:");
            sb.appendEscaped(cssColour);
            sb.appendHtmlConstant("\">");
            sb.appendHtmlConstant("</div>");

            sb.appendHtmlConstant("<div class=\"colourSwatchCell-text\">");
            sb.appendEscaped(cssColour);
            sb.appendHtmlConstant("</div>");

            sb.appendHtmlConstant("</div>");
        }
    }
}
