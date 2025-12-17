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

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class PercentBarCell extends AbstractCell<Number> {

    private static Template template;

    private final Integer warningThreshold;
    private final Integer dangerThreshold;

    public PercentBarCell() {
        if (template == null) {
            template = GWT.create(Template.class);
        }
        this.warningThreshold = null;
        this.dangerThreshold = null;
    }

    public PercentBarCell(final int warningThreshold,
                          final int dangerThreshold) {
        if (warningThreshold > dangerThreshold) {
            throw new RuntimeException("dangerThreshold should be greater than or equal to " +
                    "warningThreshold");
        }
        if (warningThreshold < 0 || warningThreshold > 100) {
            throw new RuntimeException("Invalid warningThreshold: " + warningThreshold
                    + ", should be between 0 and 100.");
        }
        if (dangerThreshold > 100) {
            throw new RuntimeException("Invalid dangerThreshold: " + dangerThreshold
                    + ", should be between 0 and 100.");
        }
        if (template == null) {
            template = GWT.create(Template.class);
        }
        this.warningThreshold = warningThreshold;
        this.dangerThreshold = dangerThreshold;
    }

    @Override
    public void render(final Context context,
                       final Number percentage,
                       final SafeHtmlBuilder sb) {

        if (percentage == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            long pctAsLong = percentage.longValue();
            if (pctAsLong < 0) {
                sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
            } else {
                if (pctAsLong > 100) {
                    pctAsLong = 100L;
                }
                final String thresholdClassName = getThresholdClassName(pctAsLong);
                final String title = pctAsLong + "%";

                final SafeStyles widthStyle = SafeStylesUtils.forWidth(pctAsLong, Unit.PCT);

                sb.append(template.percentBar(
                        thresholdClassName, widthStyle, title));
            }
        }
    }

    private String getThresholdClassName(final long percentage) {
        if (dangerThreshold != null && percentage >= dangerThreshold) {
            return "percentBarCell-bar__danger";
        } else if (warningThreshold != null && percentage >= warningThreshold) {
            return "percentBarCell-bar__warning";
        } else {
            return "percentBarCell-bar__healthy";
        }
    }


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"percentBarCell percentBarCell-container\" title=\"{2}\">"
                + "<div class=\"percentBarCell-bar {0}\" style=\"{1}\" />"
                + "</div>")
        SafeHtml percentBar(final String thresholdClassName,
                            final SafeStyles widthStyle,
                            final String title);
    }
}
