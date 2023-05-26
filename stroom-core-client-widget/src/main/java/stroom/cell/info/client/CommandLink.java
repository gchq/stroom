package stroom.cell.info.client;

import com.google.gwt.user.client.Command;

public class CommandLink {

    private final String text;
    private final Command command;

    public CommandLink(final String text, final Command command) {
        this.text = text;
        this.command = command;
    }

    public String getText() {
        return text;
    }

    public Command getCommand() {
        return command;
    }

    @Override
    public String toString() {
        return text;
    }
}
