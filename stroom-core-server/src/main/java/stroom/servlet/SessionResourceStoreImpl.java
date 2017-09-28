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

import org.springframework.stereotype.Component;
import stroom.resource.server.ResourceStore;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Wrapper for the ResourceStore that makes sure the user can only access stuff
 * in the session.
 */
@Component
public class SessionResourceStoreImpl extends HttpServlet implements SessionResourceStore {
    private static final long serialVersionUID = -4533441835216235920L;
    private static final String UUID_ARG = "UUID";

    private final ResourceStore resourceStore;
    private final HttpServletRequestHolder httpServletRequestHolder;

    @Inject
    SessionResourceStoreImpl(final ResourceStore resourceStore, final HttpServletRequestHolder httpServletRequestHolder) {
        this.resourceStore = resourceStore;
        this.httpServletRequestHolder = httpServletRequestHolder;
    }

    @Override
    public ResourceKey createTempFile(final String name) {
        final ResourceKey key = resourceStore.createTempFile(name);
        final ResourceKey sessionKey = new ResourceKey(name, UUID.randomUUID().toString());

        getMap().put(sessionKey, key);

        return sessionKey;
    }

    @Override
    public void deleteTempFile(final ResourceKey key) {
        final ResourceKey realKey = getMap().remove(key);
        if (realKey == null) {
            return;
        }
        resourceStore.deleteTempFile(realKey);
    }

    @Override
    public Path getTempFile(final ResourceKey key) {
        final ResourceKey realKey = getMap().get(key);
        if (realKey == null) {
            return null;
        }
        return resourceStore.getTempFile(getMap().get(key));
    }

    private ResourceMap getMap() {
        ResourceMap resourceMap;

        final HttpServletRequest request = httpServletRequestHolder.get();
        if (request == null) {
            throw new NullPointerException("Request holder has no current request");
        }

        final String name = "SESSION_RESOURCE_STORE";
        final HttpSession session = request.getSession();
        final Object object = session.getAttribute(name);
        if (object == null) {
            resourceMap = new ResourceMap();
            session.setAttribute(name, resourceMap);
        } else {
            resourceMap = (ResourceMap) object;
        }

        return resourceMap;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        // Get the current request.
        final HttpServletRequest originalRequest = httpServletRequestHolder.get();
        // Set this request.
        httpServletRequestHolder.set(req);

        try {
            final String uuid = req.getParameter(UUID_ARG);
            boolean found = false;
            if (uuid != null) {
                final ResourceKey resourceKey = new ResourceKey(null, uuid);
                try {
                    final Path file = getTempFile(resourceKey);
                    if (file != null && Files.isRegularFile(file)) {
                        if (file.toAbsolutePath().toString().toLowerCase().endsWith(".zip")) {
                            resp.setContentType("application/zip");
                        } else {
                            resp.setContentType("application/octet-stream");
                        }
                        resp.getOutputStream().write(Files.readAllBytes(file));
                        found = true;
                    }
                } finally {
                    deleteTempFile(resourceKey);
                }
            }

            if (!found) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Resource not found");
            }
        } finally {
            // Reset current request.
            httpServletRequestHolder.set(originalRequest);
        }
    }
}