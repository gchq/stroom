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

package stroom.util.zip;

import java.util.Enumeration;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class HeaderMapFactory {
    public HeaderMap create() {
        HeaderMap headerMap = new HeaderMap();

        HttpServletRequest httpServletRequest = getHttpServletRequest();
        addAllHeaders(httpServletRequest, headerMap);
        addAllQueryString(httpServletRequest, headerMap);

        return headerMap;
    }

    protected HttpServletRequest getHttpServletRequest() {
        HttpServletRequest httpServletRequest = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest();
        return httpServletRequest;
    }

    @SuppressWarnings("unchecked")
    private void addAllHeaders(HttpServletRequest httpServletRequest, HeaderMap headerMap) {
        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String header = headerNames.nextElement();
            headerMap.put(header, httpServletRequest.getHeader(header));
        }
    }

    private void addAllQueryString(HttpServletRequest httpServletRequest, HeaderMap headerMap) {
        String queryString = httpServletRequest.getQueryString();
        if (queryString != null) {
            StringTokenizer st = new StringTokenizer(httpServletRequest.getQueryString(), "&");
            while (st.hasMoreTokens()) {
                String pair = st.nextToken();
                int pos = pair.indexOf('=');
                if (pos != -1) {
                    String key = pair.substring(0, pos);
                    String val = pair.substring(pos + 1, pair.length());

                    headerMap.put(key, val);
                }
            }
        }
    }

}
