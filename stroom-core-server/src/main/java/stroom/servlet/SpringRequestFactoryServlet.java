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

import com.google.web.bindery.requestfactory.server.RequestFactoryServlet;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SpringRequestFactoryServlet extends RequestFactoryServlet {
    public static final String BEAN_NAME = "springRequestFactoryServlet";

    private static final long serialVersionUID = 8357087931212763451L;

    private static final ThreadLocal<ServletContext> threadLocalContext = new ThreadLocal<>();

    public static ServletContext getThreadLocalContext() {
        return threadLocalContext.get();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        threadLocalContext.set(getServletContext());
        super.doPost(request, response);
    }
}
