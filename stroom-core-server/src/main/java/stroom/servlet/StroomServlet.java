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

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamUtil;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

@Component
@Scope(StroomScope.PROTOTYPE)
public class StroomServlet extends AppServlet {
    @Inject
    public StroomServlet(final StroomPropertyService stroomPropertyService) {
        super(stroomPropertyService);
    }


    String getScript() {
        return "stroom/stroom.nocache.js";
    }
}