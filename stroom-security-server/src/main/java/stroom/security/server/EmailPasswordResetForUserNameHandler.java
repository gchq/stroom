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

package stroom.security.server;

import stroom.security.Insecure;
import stroom.security.shared.EmailPasswordResetForUserNameAction;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedBoolean;

import javax.annotation.Resource;

@TaskHandlerBean(task = EmailPasswordResetForUserNameAction.class)
@Insecure
public class EmailPasswordResetForUserNameHandler
        extends AbstractTaskHandler<EmailPasswordResetForUserNameAction, SharedBoolean> {
    @Resource
    private AuthenticationService authenticationService;

    @Override
    public SharedBoolean exec(final EmailPasswordResetForUserNameAction task) {
        return new SharedBoolean(authenticationService.emailPasswordReset(task.getResetUserName()));
    }
}
