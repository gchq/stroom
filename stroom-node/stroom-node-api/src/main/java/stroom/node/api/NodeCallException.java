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

package stroom.node.api;

import stroom.util.logging.LogUtil;

public class NodeCallException extends RuntimeException {

    private final String nodeName;
    private final String url;

    public NodeCallException(final String nodeName, final String url, final String msg) {
        super(msg);
        this.nodeName = nodeName;
        this.url = url;
    }

    public NodeCallException(final String nodeName, final String url, final Throwable throwable) {
        super(LogUtil.message("Unable to connect to node '{}' at url '{}': {}",
                nodeName, url, throwable.getMessage()), throwable);
        this.nodeName = nodeName;
        this.url = url;
    }

    public NodeCallException(final String nodeName, final String url, final String msg, final Throwable throwable) {
        super(msg, throwable);
        this.nodeName = nodeName;
        this.url = url;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return getMessage();
    }
}
