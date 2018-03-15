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

package stroom.servlet;

import org.apache.commons.io.FilenameUtils;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class CacheControlFilter implements Filter {
    private static final String GET_METHOD = "GET";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("EEE, d MMM yyyy HH:mm:ss zzz");
    private static final List<String> CACHE_FILE_TYPES = Arrays.asList("js", "css", "png", "jpg", "gif", "svg");
    private static final long DEFAULT_EXPIRES = 600; // Ten minutes

    private long seconds = DEFAULT_EXPIRES;

    @Override
    public void init(final FilterConfig filterConfig) {
        final String value = filterConfig.getInitParameter("seconds");
        if (value != null && !value.isEmpty()) {
            seconds = Long.valueOf(value);
        }
    }

    @Override
    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain filterChain) throws IOException, ServletException {
        final HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        final HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        final String filetypeRequested = FilenameUtils.getExtension(httpServletRequest.getRequestURL().toString());

        if (GET_METHOD.equals(httpServletRequest.getMethod()) && seconds > 0 && CACHE_FILE_TYPES.contains(filetypeRequested)) {
            httpServletResponse.setHeader("Cache-Control", "public, max-age=" + seconds);

            // Add an expiry time, e.g. Expires: Wed, 21 Oct 2015 07:28:00 GMT
            httpServletResponse.setHeader("Expires", getExpires(seconds));
        } else {
            httpServletResponse.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            httpServletResponse.setHeader("Expires", "0");
            httpServletResponse.setHeader("Pragma", "no-cache");
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
    }

    String getExpires(final long seconds) {
        //            Calendar c = Calendar.getInstance();
//            c.setTime(new Date());
//            c.add(Calendar.SECOND, seconds);
//            SimpleDateFormat format = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss zzz", Locale.US);
//            format.setTimeZone(TimeZone.getTimeZone("GMT"));

        ZonedDateTime dateTime = ZonedDateTime.now(ZoneId.of("GMT"));
        dateTime = dateTime.plusSeconds(seconds);
        return DATE_TIME_FORMATTER.format(dateTime);
    }
}