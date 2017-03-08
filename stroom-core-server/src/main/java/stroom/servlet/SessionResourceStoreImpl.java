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

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import stroom.resource.server.ResourceStore;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ResourceKey;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

/**
 * Wrapper for the ResourceStore that makes sure the user can only access stuff
 * in the session.
 */
@Component(SessionResourceStoreImpl.BEAN_NAME)
@Lazy
@Scope(value = StroomScope.SESSION, proxyMode = ScopedProxyMode.INTERFACES)
public class SessionResourceStoreImpl extends HttpServlet implements SessionResourceStore {
    public static final String BEAN_NAME = "sessionResourceStore";

    private static final long serialVersionUID = -4533441835216235920L;
    private static final String UUID_ARG = "UUID";
    private final HashMap<ResourceKey, ResourceKey> sessionResourceMap = new HashMap<>();
    @Resource(name = "resourceStore")
    private transient ResourceStore resourceStore;

    @Override
    public synchronized ResourceKey createTempFile(final String name) {
        final ResourceKey key = resourceStore.createTempFile(name);
        final ResourceKey sessionKey = new ResourceKey(name, UUID.randomUUID().toString());

        sessionResourceMap.put(sessionKey, key);

        return sessionKey;
    }

    @Override
    public synchronized void deleteTempFile(final ResourceKey key) {
        final ResourceKey realKey = sessionResourceMap.remove(key);
        if (realKey == null) {
            return;
        }
        resourceStore.deleteTempFile(realKey);
    }

    @Override
    public synchronized File getTempFile(final ResourceKey key) {
        final ResourceKey realKey = sessionResourceMap.get(key);
        if (realKey == null) {
            return null;
        }
        return resourceStore.getTempFile(sessionResourceMap.get(key));
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        final String uuid = req.getParameter(UUID_ARG);
        boolean found = false;
        if (uuid != null) {
            final ResourceKey resourceKey = new ResourceKey(null, uuid);
            try {
                final File file = getTempFile(resourceKey);
                if (file != null && file.isFile()) {
                    if (file.getAbsolutePath().toLowerCase().endsWith(".zip")) {
                        resp.setContentType("application/zip");
                    } else {
                        resp.setContentType("application/octet-stream");
                    }
                    StreamUtil.streamToStream(new FileInputStream(file), resp.getOutputStream(), true);
                    found = true;
                }
            } finally {
                deleteTempFile(resourceKey);
            }
        }

        if (!found) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Resource not found");
        }
    }

}
