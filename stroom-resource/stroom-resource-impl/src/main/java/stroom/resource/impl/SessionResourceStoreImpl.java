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

package stroom.resource.impl;

import stroom.resource.api.ResourceStore;
import stroom.util.servlet.HttpServletRequestHolder;
import stroom.util.shared.IsServlet;
import stroom.util.shared.ResourceKey;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/**
 * Wrapper for the ResourceStore that makes sure the user can only access stuff
 * in the session.
 */
public class SessionResourceStoreImpl extends HttpServlet implements ResourceStore, IsServlet {

    private static final Set<String> PATH_SPECS = Set.of("/resourcestore/*");
    private static final String UUID_ARG = "uuid";
    private static final String ZIP_EXTENSION = ".zip";

    private final ResourceStoreImpl resourceStore;
    private final HttpServletRequestHolder httpServletRequestHolder;

    @Inject
    SessionResourceStoreImpl(final ResourceStoreImpl resourceStore,
                             final HttpServletRequestHolder httpServletRequestHolder) {
        this.resourceStore = resourceStore;
        this.httpServletRequestHolder = httpServletRequestHolder;
    }

    @Override
    public ResourceKey createTempFile(final String name) {
        final ResourceKey key = resourceStore.createTempFile(name);
        final ResourceKey sessionKey = new ResourceKey(UUID.randomUUID().toString(), name);

        getMap().put(sessionKey, key);

        return sessionKey;
    }

    @Override
    public Path getTempFile(final ResourceKey key) {
        final ResourceKey realKey = getRealKey(key);
        if (realKey == null) {
            return null;
        }
        return resourceStore.getTempFile(realKey);
    }

    @Override
    public void deleteTempFile(final ResourceKey key) {
        final ResourceKey realKey = getRealKey(key);
        if (realKey == null) {
            return;
        }
        resourceStore.deleteTempFile(realKey);
    }

    private ResourceKey getRealKey(final ResourceKey key) {
        return getMap().get(key);
    }

    private ResourceMap getMap() {
        final ResourceMap resourceMap;

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
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        // Get the current request.
        final HttpServletRequest originalRequest = httpServletRequestHolder.get();
        // Set this request.
        httpServletRequestHolder.set(req);

        try {
            final String uuid = req.getParameter(UUID_ARG);
            boolean found = false;
            if (uuid != null) {
                final ResourceKey resourceKey = getRealKey(new ResourceKey(uuid, null));
                if (resourceKey != null) {
                    try {
                        final Path tempFile = resourceStore.getTempFile(resourceKey);
                        if (tempFile != null && Files.isRegularFile(tempFile)) {
                            if (resourceKey.getName().toLowerCase().endsWith(ZIP_EXTENSION)) {
                                resp.setContentType("application/zip");
                            } else {
                                resp.setContentType("application/octet-stream");
                            }
                            resp.getOutputStream().write(Files.readAllBytes(tempFile));
                            found = true;
                        }
                    } finally {
                        deleteTempFile(resourceKey);
                    }
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

    /**
     * @return The part of the path that will be in addition to any base path,
     * e.g. "/datafeed".
     */
    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }
}
