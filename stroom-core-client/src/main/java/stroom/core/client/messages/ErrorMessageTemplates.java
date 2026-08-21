/*
 * Copyright 2025 Crown Copyright
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

package stroom.core.client.messages;

import com.google.gwt.i18n.client.Messages;

public interface ErrorMessageTemplates extends Messages {
    @DefaultMessage("{0}: {1}")
    String errorMessage(String severity, String message);

    @DefaultMessage("{0}: {1} (node: {2})")
    String errorMessageWithNode(String severity, String message, String node);

    @DefaultMessage("The following message has been created while running this search:")
    String errorMessageCreatedSingular();

    @DefaultMessage("The following messages have been created while running this search:")
    String errorMessagesCreatedPlural();

}
