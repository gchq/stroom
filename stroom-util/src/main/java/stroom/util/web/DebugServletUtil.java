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

package stroom.util.web;

import stroom.util.io.StreamUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Enumeration;

/**
 * Utility between Stroom and Stroom PROXY
 */
public final class DebugServletUtil {

    public static void doPost(final HttpServletRequest req,
                              final HttpServletResponse resp) throws ServletException, IOException {
        final StringBuilder debugResponse = new StringBuilder();

        debugResponse.append("\n");
        debugResponse.append("HTTP Header\n");
        debugResponse.append("===========\n");

        @SuppressWarnings("unchecked") final Enumeration<String> headers = req.getHeaderNames();

        while (headers.hasMoreElements()) {
            final String headerKey = headers.nextElement();
            final String headerValue = req.getHeader(headerKey);
            debugResponse.append("[" + headerKey + "]=[" + headerValue + "]\n");
        }

        debugResponse.append("\n");
        debugResponse.append("HTTP Header\n");
        debugResponse.append("===========\n");
        debugResponse.append("contentLength=" + req.getContentLength());

        debugResponse.append("\n");

        debugResponse.append("HTTP Payload\n");
        debugResponse.append("============\n");
        final String payload = StreamUtil.streamToString(req.getInputStream());
        debugResponse.append(payload + "\n");

        debugResponse.append("\n");

        resp.getWriter().write(debugResponse.toString());
        resp.setStatus(HttpServletResponse.SC_OK);
    }

}
