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

package stroom.visualisation.client.presenter;

import java.util.HashSet;
import java.util.Set;

public class VisFunction {
    private final int id;
    private String url;
    private String functionName;
    private Set<StatusHandler> statusHandlers;
    private LoadStatus status = LoadStatus.NOT_LOADED;
    private String statusMessage;

    public VisFunction(final int id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(final String functionName) {
        this.functionName = functionName;
    }

    public void setStatus(final LoadStatus status, final String statusMessage) {
        this.status = status;
        this.statusMessage = statusMessage;

        if (statusHandlers != null) {
            for (final StatusHandler handler : statusHandlers) {
                handler.onChange(this);
            }
        }
    }

    public LoadStatus getStatus() {
        return status;
    }

    public void setStatus(final LoadStatus status) {
        setStatus(status, null);
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void addStatusHandler(final StatusHandler handler) {
        if (statusHandlers == null) {
            statusHandlers = new HashSet<>();
        }
        statusHandlers.add(handler);
        handler.onChange(this);
    }

    public void removeStatusHandler(final StatusHandler handler) {
        if (statusHandlers != null) {
            statusHandlers.remove(handler);
            if (statusHandlers.size() == 0) {
                statusHandlers = null;
            }
        }
    }

    public enum LoadStatus {
        NOT_LOADED, LOADING_ENTITY, LOADING_SCRIPT, INJECTING_SCRIPT, LOADED, FAILURE
    }

    public interface StatusHandler {
        void onChange(VisFunction function);
    }
}
