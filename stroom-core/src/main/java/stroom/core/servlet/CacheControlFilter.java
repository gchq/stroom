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

package stroom.core.servlet;

import stroom.util.shared.ResourcePaths;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class CacheControlFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheControlFilter.class);

    public static String INIT_PARAM_KEY_SECONDS = "seconds";
    public static String INIT_PARAM_KEY_CACHEABLE_PATH_REGEX = "cacheablePathRegex";

    private static final String GET_METHOD = "GET";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
    private static final Set<String> CACHEABLE_FILE_TYPES = new HashSet<>(Arrays.asList(
            "js", "css", "png", "jpg", "gif", "svg", "ico", "gif", "jpeg", "woff", "woff2", "eot", "ttf", "webp"));
    private static final String GWT_NO_CACHE = ".nocache.";
    private static final long DEFAULT_EXPIRES = 600; // Ten minutes

    private long seconds = DEFAULT_EXPIRES;
    private Pattern cacheablePathRegex;

    @Override
    public void init(final FilterConfig filterConfig) {
        final String value = filterConfig.getInitParameter(INIT_PARAM_KEY_SECONDS);
        if (value != null && !value.isEmpty()) {
            seconds = Long.valueOf(value);
        }
        final String cacheablePathRegexStr = filterConfig.getInitParameter(INIT_PARAM_KEY_CACHEABLE_PATH_REGEX);
        Objects.requireNonNull(cacheablePathRegexStr);
        cacheablePathRegex = Pattern.compile(cacheablePathRegexStr);
    }

    @Override
    public void doFilter(final ServletRequest servletRequest,
                         final ServletResponse servletResponse,
                         final FilterChain filterChain) throws IOException, ServletException {
        final HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        final HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        final String filetypeRequested = FilenameUtils.getExtension(httpServletRequest.getRequestURL().toString());
        final String requestUri = httpServletRequest.getRequestURI();

        // Allow static files to be cached by nginx or the browser, but not gwt files that are
        // explicitly marked for not caching.
        if (GET_METHOD.equals(httpServletRequest.getMethod())
                && seconds > 0
                //&& (CACHEABLE_FILE_TYPES.contains(filetypeRequested) || isCacheablePath(requestUri))
                && !requestUri.contains(ResourcePaths.API_ROOT_PATH)
                && !requestUri.contains(GWT_NO_CACHE)) {
            httpServletResponse.setHeader("Cache-Control", "public, max-age=" + seconds);

            // Add an expiry time, e.g. Expires: Wed, 21 Oct 2015 07:28:00 GMT
            httpServletResponse.setHeader("Expires", getExpires(seconds));
            LOGGER.trace("{} is cacheable", requestUri);
        } else {
            httpServletResponse.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            httpServletResponse.setHeader("Expires", "0");
            httpServletResponse.setHeader("Pragma", "no-cache");
            LOGGER.trace("{} is not cacheable", requestUri);
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private boolean isCacheablePath(final String requestUri) {
        return cacheablePathRegex.matcher(requestUri).matches();
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
