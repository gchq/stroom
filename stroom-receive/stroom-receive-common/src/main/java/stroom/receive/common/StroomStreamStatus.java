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

package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;

public class StroomStreamStatus {

    private final StroomStatusCode stroomStatusCode;
    private final AttributeMap attributeMap;

    public StroomStreamStatus(final StroomStatusCode stroomStatusCode,
                              final AttributeMap attributeMap) {
        this.stroomStatusCode = stroomStatusCode;
        this.attributeMap = attributeMap;
    }

    public StroomStatusCode getStroomStatusCode() {
        return stroomStatusCode;
    }

    public AttributeMap getAttributeMap() {
        return attributeMap;
    }

    public String buildStatusMessage(final Object... args) {
        final StringBuilder builder = new StringBuilder();
        builder.append("Stroom Status ");
        if (stroomStatusCode != null) {
            builder.append(stroomStatusCode.getCode())
                    .append(" - ")
                    .append(stroomStatusCode.getMessage());
        } else {
            builder.append("null");
        }

        AttributeMapUtil.appendAttributes(
                attributeMap,
                builder,
                StandardHeaderArguments.FEED,
                StandardHeaderArguments.COMPRESSION,
                StandardHeaderArguments.TYPE);

        if (args != null) {
            for (final Object object : args) {
                builder.append(" - ")
                        .append(object);
            }
        }
        return builder.toString();
    }

    public String toString() {
        final StringBuilder clientDetailsStringBuilder = new StringBuilder();
        AttributeMapUtil.appendAttributes(
                attributeMap,
                clientDetailsStringBuilder,
                StandardHeaderArguments.X_FORWARDED_FOR,
                StandardHeaderArguments.REMOTE_HOST,
                StandardHeaderArguments.REMOTE_ADDRESS,
                StandardHeaderArguments.RECEIVED_PATH,
                StandardHeaderArguments.RECEIPT_ID,
                StandardHeaderArguments.DATA_RECEIPT_RULE);

        final String clientDetailsStr = clientDetailsStringBuilder.isEmpty()
                ? ""
                : " -" + clientDetailsStringBuilder;

        return stroomStatusCode.getHttpCode() + " - " + buildStatusMessage() + clientDetailsStr;
    }
}
