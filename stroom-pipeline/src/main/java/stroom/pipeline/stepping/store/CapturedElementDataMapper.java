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

package stroom.pipeline.stepping.store;

import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.xml.event.EventListSerializer;
import stroom.pipeline.xml.event.EventListUtils;

/**
 * Renders a stored {@link CapturedElementData} into the wire {@link SharedElementData} the client displays -
 * text on both sides.
 * <p>
 * A {@code SAX_EVENTS} side is rendered as {@code getXML(buildNodeInfo(events))}, i.e. through the Saxon tree
 * serialiser that produced the text before the store held events. That is what keeps display text
 * byte-identical, and is why {@code EventListUtils.getXML(EventList)} (a different, JAXP serialiser) must
 * <b>not</b> be used here.
 */
public final class CapturedElementDataMapper {

    private CapturedElementDataMapper() {
    }

    public static SharedElementData toShared(final CapturedElementData data) {
        if (data == null) {
            return null;
        }
        return new SharedElementData(
                displayText(data.input()),
                displayText(data.output()),
                data.indicators(),
                data.formatInput(),
                data.formatOutput(),
                data.hasOutput());
    }

    private static String displayText(final CapturedData captured) {
        if (captured == null) {
            return null;
        }
        if (captured.isSaxEvents()) {
            return EventListUtils.getXML(EventListUtils.buildNodeInfo(
                    EventListSerializer.fromBytes(captured.data())));
        }
        return captured.asText();
    }
}
