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

package stroom.ai.client;

import stroom.ai.shared.AiChat;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.Templates;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

public class AiChatCell extends AbstractCell<AiChat> {

    @Override
    public void render(final Context context, final AiChat value, final SafeHtmlBuilder sb) {
        if (value != null) {
            final SafeHtmlBuilder row = new SafeHtmlBuilder();

            // Add title
            row.append(Templates.div(getCellClassName() + "-title",
                    value.getTitle(),
                    SafeHtmlUtil.from(value.getTitle())));

            // Add age
            row.append(Templates.div(getCellClassName() + "-age",
                    SafeHtmlUtil.from(RelativeTimeUtil.formatRelativeTime(value.getUpdateTimeMs()))));

            sb.append(Templates.div(getCellClassName() + "-row", row.toSafeHtml()));
        }
    }

    private String getCellClassName() {
        return "aiChatHistoryCell";
    }
}
