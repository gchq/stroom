package stroom.langchain.api;

import stroom.util.logging.LogUtil;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("all")
public class TableSummaryMessages {

    private static final String SYSTEM_MESSAGE = "You merge partial answers into a unified, concise summary.";
    private static final String USER_MESSAGE = """
                Merge the following TWO summaries into a single improved summary.
                Preserve important details and remove duplicates.
            
                SUMMARY A:
                {}
            
                SUMMARY B:
                {}
            """;

//    private static final String SUMMARY_A = """
//                Merge the following TWO summaries into a single improved summary.
//                Preserve important details and remove duplicates.
//
//                SUMMARY A:
//
//            """;
//    private static final String SUMMARY_B = """
//
//                SUMMARY B:
//
//            """;

    public static List<ChatMessage> createMessages(final String summaryA, final String summaryB) {
        final List<ChatMessage> messages = new ArrayList<>(2);
        messages.add(new SystemMessage(SYSTEM_MESSAGE));
        final String message = LogUtil.message(USER_MESSAGE, summaryA, summaryB);
        messages.add(new UserMessage(message));
        return messages;
    }
}
