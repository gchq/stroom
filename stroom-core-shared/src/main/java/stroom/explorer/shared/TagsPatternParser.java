package stroom.explorer.shared;


import java.util.ArrayList;
import java.util.List;

public class TagsPatternParser {

    private static final String TAG_TOKEN = "tag:";

    private final List<String> tags = new ArrayList<>();
    private final StringBuilder textBuilder = new StringBuilder();

    public TagsPatternParser(final String inputText) {
        parse(inputText);
    }

    private void parse(final String inputText) {
        final String[] tokens = inputText.split("\\s+");

        for (final String token : tokens) {
            if (token.startsWith(TAG_TOKEN)) {
                // Extract the value after "tag:"
                final String value = token.substring(TAG_TOKEN.length());
                if (!value.isEmpty()) {
                    tags.add(value);
                }
            } else {
                if (textBuilder.length() > 0) {
                    textBuilder.append(" ");
                }
                textBuilder.append(token);
            }
        }
    }

    public List<String> getTags() {
        return tags;
    }

    public String getText() {
        return textBuilder.toString();
    }
}
