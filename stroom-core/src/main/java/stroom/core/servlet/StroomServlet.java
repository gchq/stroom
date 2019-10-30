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

package stroom.core.servlet;

import stroom.ui.config.shared.UiConfig;
import stroom.util.shared.IsServlet;

import javax.inject.Inject;
import java.util.Set;

public class StroomServlet extends AppServlet implements IsServlet {
    private static final Set<String> PATH_SPECS = Set.of("/ui");

    @Inject
    StroomServlet(final UiConfig uiConfig) {
        super(uiConfig);
    }

    String getScript() {
        return "stroom/stroom.nocache.js";
    }

    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }
}