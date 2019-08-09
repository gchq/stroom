package stroom.util;

import java.util.List;
import java.util.regex.Pattern;

public class NextNameGenerator {
    /**
     * Returns a new name for a list of names that look like this: "New group (1)", "New group (2)".
     *
     * When creating something new a user might create multiple new items before renaming them.
     * If the name is supposed to be unique, or is part of a key then we can't just use the same
     * name over and over. Nor would we want to because it means the new things might be somewhat
     * indistinguishable to the user.
     *
     * So this method generates a new name from a list of names and a regex. The names must take
     * the form of, for example, "New cat meme (n)", where "n" is the number of new cat memes we're up to.
     * I.e. "New cat meme (1)", "New cat meme (2)". The regex needs to identify
     * the number in these parenthesis so it can increment it, so the regex needs a capture
     * group around this. For example: "New group [(]([0-9]+)[)]"
     *
     * @param names A list of all (or all new) item names, so we can create something new
     * @param newNamePrefix The prefix for the new name, e.g. "New cat meme"
     * @param regex A regex that captures the incremented number.
     * @return A new, incremented name.
     */
    public static String getNextName(final List<String> names, final String newNamePrefix, final String regex) {
        var pattern = Pattern.compile(String.format("%s %s", newNamePrefix, regex));
        var nextName = names.stream()
                // We only care about names in the new name format
                .filter(name -> pattern.matcher(name).find())
                // We only care about the increments, so let's extract those
                .map(name -> {
                    var matcher = pattern.matcher(name);
                    matcher.find();
                    return Integer.parseInt(matcher.group(1));
                })
                // Sort descending
                .sorted((a, b) -> b - a)
                .findFirst()
                // Generate the new name based on the optional returned from findFirst().
                .map((increment) -> String.format("%s (%s)", newNamePrefix, increment + 1))
                .orElse(String.format("%s (1)", newNamePrefix));

        return nextName;
    }

    /**
     * A helper that supplies a default increment regex to getNextName(...)
     */
    public static String getNextName(final List<String> names, final String newNamePrefix) {
        final String incrementCountRegex = "[(]([0-9]+)[)]";
        return getNextName(names, newNamePrefix, incrementCountRegex);
    }
}
