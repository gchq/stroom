package stroom.langchain.api;

import stroom.util.logging.LogUtil;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("all")
public class TableQueryMessages {

//    private static final String SYSTEM_MESSAGE = """
//                You are a data analysis AI. You will answer user questions
//                using ONLY the markdown-formatted DATA TABLE records provided.
//                If the records do not contain relevant details, say "No relevant information."
//            """;

    private static final String SYSTEM_MESSAGE = """
                You are a data analysis AI. You will answer user questions
                using ONLY the markdown-formatted DATA TABLE records provided.
            """;
    private static final String USER_MESSAGE = """
                USER QUERY:
                {}
            
                DATA TABLE:
                {}}
            
                Provide findings relevant only to these records, in a concise structured format.
            """;
//
//    private static final String PRE = """

    /// /                        Merge the following TWO summaries into a single improved summary.
    /// /                        Preserve important details and remove duplicates.
    /// /
    /// /                        SUMMARY A:
    /// /                        {{a}}
    /// /
    /// /                        SUMMARY B:
    /// /                        {{b}}
    /// /                    """
//    private static final String MID = """
//
//                DATA TABLE:
//
//            """;
//    private static final String POST = """
//
//                Provide findings relevant only to these records, in a concise structured format.
//            """;
    public static List<ChatMessage> createMessages(final String query, final String table) {
        final List<ChatMessage> messages = new ArrayList<>(2);
        messages.add(new SystemMessage(SYSTEM_MESSAGE));
        final String message = LogUtil.message(USER_MESSAGE, query, table);
        messages.add(new UserMessage(message));
        return messages;
    }
}
