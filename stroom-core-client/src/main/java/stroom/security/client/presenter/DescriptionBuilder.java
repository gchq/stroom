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

package stroom.security.client.presenter;

import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

public class DescriptionBuilder {

    private final SafeHtmlBuilder sb = new SafeHtmlBuilder();
    private boolean written;

    public void addLine(final String text) {
        addLine(false, false, false, SafeHtmlUtil.getSafeHtml(text));
    }

    public void addLine(final boolean isBold,
                        final boolean isInherited,
                        final String text) {
        addLine(isBold, isInherited, false, SafeHtmlUtil.getSafeHtml(text));
    }

    public void addLine(final boolean isBold,
                        final boolean isInherited,
                        final SafeHtml text) {
        addLine(isBold, isInherited, false, text);
    }

    public void addLine(final boolean isBold,
                        final boolean isInherited,
                        final boolean isDelimiter,
                        final SafeHtml text) {
        final ClassNameBuilder classNameBuilder = new ClassNameBuilder();
        if (isInherited) {
            classNameBuilder.addClassName("inherited");
        }
        if (isBold) {
            classNameBuilder.addClassName("bold");
        }
        if (isDelimiter) {
            classNameBuilder.addClassName("delimiter");
        }

        if (classNameBuilder.isEmpty()) {
            // Pointless span but not sure if any css is relying on it, so leaving it in for now
            sb.appendHtmlConstant("<span>")
                    .append(text)
                    .appendHtmlConstant("</span");
        } else {
            sb.append(SafeHtmlUtil.getTemplate().spanWithClass(classNameBuilder.build(), text));
        }
        written = true;
    }

    public void addTitle(final String title) {
        addLine(true, false, title);
    }

    public void append(final SafeHtml safeHtml) {
        sb.append(safeHtml);
        written = true;
    }

    public void addNewLine() {
        if (written) {
            sb.appendHtmlConstant("<br/>");
        }
    }

    public SafeHtml toSafeHtml() {
        return sb.toSafeHtml();
    }
}
