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

import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

/**
 * Shows an icon in a DataGrid cell.
 * Note: this does NOT ensure the SVG is safe to display!
 */
public class SvgIconCell extends AbstractCell<String> {

    /**
     * Render the cell.
     * @param context the {@link Context} of the cell
     * @param value the cell value to be rendered
     * @param sb the {@link SafeHtmlBuilder} to be written to
     */
    @Override
    public void render(final Context context, final String value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final String className = "svgCell-icon";

            sb.append(SvgImageUtil.toSafeHtml((String) null, value, className));
        }
    }
}
