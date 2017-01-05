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

package stroom.util.logging;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

/**
 * start / stop / refresh Log4J configuration
 */
@SuppressWarnings("MTIA_SUSPECT_SERVLET_INSTANCE_FIELD")
public class Log4JServlet extends HttpServlet {
    private static final long serialVersionUID = 8833402961986851281L;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        Log4JInstance.getInstance().init(config.getServletContext());
    }

    @Override
    public void destroy() {
        Log4JInstance.getInstance().destroy(getServletContext());
    }
}
