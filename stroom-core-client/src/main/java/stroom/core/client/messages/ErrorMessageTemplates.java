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
