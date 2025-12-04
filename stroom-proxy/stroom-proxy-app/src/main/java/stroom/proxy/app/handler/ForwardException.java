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

package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.handler.HttpSender.ResponseStatus;
import stroom.util.shared.NullSafe;

import java.util.Objects;

public class ForwardException extends RuntimeException {

    private final StroomStatusCode stroomStatusCode;
    private final String feedName;
    private final boolean isRecoverable;
    private final int httpResponseCode; // In the case of UNKNOWN_ERROR, this may differ from 999

    private ForwardException(final StroomStatusCode stroomStatusCode,
                             final AttributeMap attributeMap,
                             final String message,
                             final int httpResponseCode,
                             final boolean isRecoverable,
                             final Throwable cause) {
        super(message, cause);
        this.isRecoverable = isRecoverable;
        this.stroomStatusCode = stroomStatusCode;
        this.httpResponseCode = httpResponseCode;
        this.feedName = NullSafe.get(
                attributeMap,
                attrMap -> attrMap.get(StandardHeaderArguments.FEED));
    }

    public static ForwardException recoverable(final StroomStatusCode stroomStatusCode,
                                               final AttributeMap attributeMap,
                                               final String message,
                                               final Throwable cause) {
        return new ForwardException(
                stroomStatusCode,
                attributeMap,
                message,
                stroomStatusCode.getHttpCode(),
                true,
                cause);
    }

    public static ForwardException recoverable(final ResponseStatus responseStatus,
                                               final AttributeMap attributeMap) {
        Objects.requireNonNull(responseStatus);
        final StroomStatusCode stroomStatusCode = responseStatus.stroomStatusCode();
        return new ForwardException(
                stroomStatusCode,
                attributeMap,
                Objects.requireNonNullElse(responseStatus.message(), stroomStatusCode.getMessage()),
                responseStatus.httpResponseCode(),
                true,
                null);
    }

    public static ForwardException nonRecoverable(final StroomStatusCode stroomStatusCode,
                                                  final AttributeMap attributeMap,
                                                  final String message,
                                                  final Throwable cause) {
        return new ForwardException(
                stroomStatusCode,
                attributeMap,
                message,
                stroomStatusCode.getHttpCode(),
                false,
                cause);
    }

    public static ForwardException nonRecoverable(final ResponseStatus responseStatus,
                                                  final AttributeMap attributeMap) {
        Objects.requireNonNull(responseStatus);
        final StroomStatusCode stroomStatusCode = responseStatus.stroomStatusCode();
        return new ForwardException(
                stroomStatusCode,
                attributeMap,
                Objects.requireNonNullElse(responseStatus.message(), stroomStatusCode.getMessage()),
                responseStatus.httpResponseCode(),
                false,
                null);
    }

    public boolean isRecoverable() {
        return isRecoverable;
    }

    public String getFeedName() {
        return feedName;
    }

    public int getHttpResponseCode() {
        return httpResponseCode;
    }
}
