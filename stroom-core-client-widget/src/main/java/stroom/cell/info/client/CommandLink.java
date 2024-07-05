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

    public String getTooltip() {
        return tooltip;
    }

    @Override
    public String toString() {
        return text;
    }
}
