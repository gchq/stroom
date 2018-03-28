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

package stroom.task;

import stroom.util.logging.LoggerUtil;
import stroom.util.shared.HasTerminate;
import stroom.task.Monitor;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MonitorImpl implements Monitor {
    private static final long serialVersionUID = 6158410874438193810L;

    private final Set<Monitor> children = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile boolean terminate;
    private volatile Object[] info;
    private volatile String name;

    @Override
    public boolean isTerminated() {
        return terminate;
    }

    @Override
    public void terminate() {
        this.terminate = true;
        children.forEach(HasTerminate::terminate);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public String getInfo() {
        return LoggerUtil.buildMessage(info);
    }

    @Override
    public void info(final Object... args) {
        this.info = args;
    }

    @Override
    public void addChild(final Monitor monitor) {
        children.add(monitor);
        if (terminate) {
            monitor.terminate();
        }
    }

    @Override
    public void removeChild(final Monitor monitor) {
        children.remove(monitor);
    }

    @Override
    public Set<Monitor> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        return getInfo();
    }
}
