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

package stroom.cell.info.client;

import com.google.gwt.user.client.Command;

@SuppressWarnings("ClassCanBeRecord")
public class CommandLink {

    private final String text;
    private final Command command;
    private final String tooltip;

    public CommandLink(final String text, final String tooltip, final Command command) {
        this.text = text;
        this.command = command;
        this.tooltip = tooltip;
    }

    public CommandLink(final String text, final Command command) {
        this.text = text;
        this.command = command;
        this.tooltip = null;
    }

    public static CommandLink withoutCommand(final String text) {
        return new CommandLink(text, null, null);
    }

    public String getText() {
        return text;
    }

    public Command getCommand() {
        return command;
    }

    public boolean hasCommand() {
        return command != null;
    }

    public String getTooltip() {
        return tooltip;
    }

    @Override
    public String toString() {
        return text;
    }
}
