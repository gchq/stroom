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

package stroom.datafeed.server;

import org.springframework.stereotype.Component;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.ReadListener;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

@Component
public class MockHttpServletRequest implements HttpServletRequest {
    private Map<String, String> headers = new HashMap<>();
    private byte[] inputStreamData;
    private InputStream inputStream;
    private String queryString;

    public MockHttpServletRequest() {
    }

    public void resetMock() {
        headers = new HashMap<>();
        inputStream = null;
        inputStreamData = null;
        queryString = null;
    }

    public void addHeader(final String key, final String value) {
        headers.put(key, value);
    }

    public void setInputStream(final byte[] buffer) {
        inputStreamData = buffer;
    }

    @Override
    public String getAuthType() {
        return null;
    }

    @Override
    public String getContextPath() {
        return null;
    }

    @Override
    public Cookie[] getCookies() {
        return null;
    }

    @Override
    public long getDateHeader(final String arg0) {
        return 0;
    }

    @Override
    public String getHeader(final String arg0) {
        return headers.get(arg0);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Enumeration getHeaderNames() {
        final Vector<String> keys = new Vector<String>();
        keys.addAll(headers.keySet());
        return keys.elements();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Enumeration getHeaders(final String arg0) {
        return null;
    }

    @Override
    public int getIntHeader(final String arg0) {
        return 0;
    }

    @Override
    public String getMethod() {
        return null;
    }

    @Override
    public String getPathInfo() {
        return null;
    }

    @Override
    public String getPathTranslated() {
        return null;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(final String str) {
        queryString = str;
    }

    @Override
    public String getRemoteUser() {
        return null;
    }

    @Override
    public String getRequestURI() {
        return null;
    }

    @Override
    public StringBuffer getRequestURL() {
        return null;
    }

    @Override
    public String getRequestedSessionId() {
        return null;
    }

    @Override
    public String getServletPath() {
        return null;
    }

    @Override
    public HttpSession getSession() {
        return null;
    }

    @Override
    public HttpSession getSession(final boolean arg0) {
        return null;
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return false;
    }

    @Override
    public boolean isUserInRole(final String arg0) {
        return false;
    }

    @Override
    public Object getAttribute(final String arg0) {
        return null;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Enumeration getAttributeNames() {
        return new Vector<String>().elements();
    }

    @Override
    public String getCharacterEncoding() {
        return null;
    }

    @Override
    public void setCharacterEncoding(final String arg0) throws UnsupportedEncodingException {
    }

    @Override
    public int getContentLength() {
        return 0;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (inputStreamData != null) {
            return new ServletInputStreamImpl(new ByteArrayInputStream(inputStreamData));
        }
        if (inputStream != null) {
            return new ServletInputStreamImpl(inputStream);
        }
        return null;

    }

    public void setInputStream(final InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public String getLocalAddr() {
        return null;
    }

    @Override
    public String getLocalName() {
        return null;
    }

    @Override
    public int getLocalPort() {
        return 0;
    }

    @Override
    public Locale getLocale() {
        return null;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Enumeration getLocales() {
        return null;
    }

    @Override
    public String getParameter(final String arg0) {
        return null;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Map getParameterMap() {
        return null;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Enumeration getParameterNames() {
        return new Vector<String>().elements();
    }

    @Override
    public String[] getParameterValues(final String arg0) {
        return null;
    }

    @Override
    public String getProtocol() {
        return null;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return null;
    }

    @Override
    public String getRealPath(final String arg0) {
        return null;
    }

    @Override
    public String getRemoteAddr() {
        return null;
    }

    @Override
    public String getRemoteHost() {
        return null;
    }

    @Override
    public int getRemotePort() {
        return 0;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(final String arg0) {
        return null;
    }

    @Override
    public String getScheme() {
        return null;
    }

    @Override
    public String getServerName() {
        return null;
    }

    @Override
    public int getServerPort() {
        return 0;
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public void removeAttribute(final String arg0) {
    }

    @Override
    public void setAttribute(final String arg0, final Object arg1) {
    }

    public static class ServletInputStreamImpl extends ServletInputStream {
        private final InputStream inputStream;

        public ServletInputStreamImpl(final InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public int available() throws IOException {
            return inputStream.available();
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }

        @Override
        public synchronized void mark(final int readlimit) {
            inputStream.mark(readlimit);
        }

        @Override
        public boolean markSupported() {
            return inputStream.markSupported();
        }

        @Override
        public int read() throws IOException {
            return inputStream.read();
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            return inputStream.read(b, off, len);
        }

        @Override
        public int read(final byte[] b) throws IOException {
            return inputStream.read(b);
        }

        @Override
        public synchronized void reset() throws IOException {
            inputStream.reset();
        }

        @Override
        public long skip(final long n) throws IOException {
            return inputStream.skip(n);
        }

        @Override
        public boolean equals(final Object obj) {
            return inputStream.equals(obj);
        }

        @Override
        public int hashCode() {
            return inputStream.hashCode();
        }

        @Override
        public String toString() {
            return inputStream.toString();
        }

    }
}
