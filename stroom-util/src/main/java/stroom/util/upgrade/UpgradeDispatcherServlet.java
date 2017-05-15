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

package stroom.util.upgrade;

import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * Servlet that does some upgrade checking / work before starting.
 */
public class UpgradeDispatcherServlet extends DispatcherServlet {
    private static final long serialVersionUID = -4794349034486725368L;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        UpgradeDispatcherSingleton.instance().init(this, config);
    }

    public void doInit(final ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    public void service(final ServletRequest request, final ServletResponse response)
            throws ServletException, IOException {
        if (UpgradeDispatcherSingleton.instance().isDispatcherStarted()) {
            super.service(request, response);
        } else {
            UpgradeDispatcherSingleton.instance().service(request, response);
        }
    }

    @Override
    public void destroy() {
        UpgradeDispatcherSingleton.instance().destroy();
    }

    public void doDestroy() {
        super.destroy();
    }
}
