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

package stroom.data.store.impl.fs;

import stroom.util.io.StreamUtil;
import stroom.util.shared.IsServlet;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.Unauthenticated;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Unauthenticated
public class EchoServlet extends HttpServlet implements IsServlet {

    private static final long serialVersionUID = -2569496543022536282L;

    public static final String PATH_PART = "/echo";
    private static final Set<String> PATH_SPECS = Set.of(
            PATH_PART,
            ResourcePaths.addLegacyUnauthenticatedServletPrefix(PATH_PART));

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        try (final InputStream is = new BlockGZIPInputStream(req.getInputStream())) {
            resp.setStatus(200);
            StreamUtil.streamToStream(is, resp.getOutputStream());
        }
    }

    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }
}
