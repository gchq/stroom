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

package stroom.dispatch.client;

class ResponseException extends RuntimeException {

    private final String method;
    private final String url;
    private final String json;
    private final Integer code;
    private final String details;
    private final String responseKeyValues;

    ResponseException(final String method,
                      final String url,
                      final String json,
                      final Integer code,
                      final String message,
                      final String details,
                      final String responseKeyValues,
                      final Throwable cause) {
        super(message, cause);
        this.method = method;
        this.url = url;
        this.json = json;
        this.code = code;
        this.details = details;
        this.responseKeyValues = responseKeyValues;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return "ResponseException{" +
                "method='" + method + '\'' +
                ", url='" + url + '\'' +
                ", json='" + json + '\'' +
                ", code='" + code + '\'' +
                ", message='" + getMessage() + '\'' +
                ", details='" + details + '\'' +
                ", responseKeyValues='" + responseKeyValues + '\'' +
                '}';
    }
}
