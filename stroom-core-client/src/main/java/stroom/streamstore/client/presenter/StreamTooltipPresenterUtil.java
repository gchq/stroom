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

package stroom.streamstore.client.presenter;

import stroom.entity.shared.NamedEntity;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.tooltip.client.presenter.TooltipUtil;

public class StreamTooltipPresenterUtil {
    public static final void addRowDateString(final StringBuilder html, final String label, final Long ms) {
        if (ms != null) {
            TooltipUtil.addRowData(html, label, ClientDateUtil.createDateTimeString(ms) + " (" + ms + ")");
        }
    }

    public static final void addRowNameString(final StringBuilder html, final String label,
            final NamedEntity namedEntity) {
        if (namedEntity != null) {
            TooltipUtil.addRowData(html, label, namedEntity.getName() + " (" + namedEntity.getId() + ")");
        }
    }
}
