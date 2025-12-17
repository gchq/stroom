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

package stroom.util.shared;

import java.io.Serializable;

/**
 * Base class of all Stroom service exceptions. These exceptions can be passed up
 * to GWT and so can unwrap all exceptions into nice messages for the user.
 */
public class EntityServiceException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = -6840395605715731686L;

    /**
     * Don't use the actual class name as GWT won't know it!
     */
    private String causeClassName;
    private boolean networkRelated;

    /**
     * Some other technical detail
     */
    private String detail;

    /**
     * Server Rendered Call Stacks
     */
    private String callStack;

    public EntityServiceException() {
    }

    public EntityServiceException(final String message) {
        super(message);
    }

    public EntityServiceException(final String message, final String causeClassName, final boolean networkRelated) {
        super(message);
        this.causeClassName = causeClassName;
        this.networkRelated = networkRelated;
        setDetail(causeClassName);
    }

    public String getCauseClassName() {
        return causeClassName;
    }

    public boolean isNetworkRelated() {
        return networkRelated;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(final String detail) {
        this.detail = detail;
    }

    public String getCallStack() {
        return callStack;
    }

    public void setCallStack(final String callStack) {
        this.callStack = callStack;
    }

    @Override
    public String toString() {
        if (detail != null) {
            return super.toString() + " " + getDetail();
        }
        return super.toString();

    }
}
