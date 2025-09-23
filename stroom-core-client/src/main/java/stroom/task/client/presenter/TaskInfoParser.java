package stroom.task.client.presenter;

public class TaskInfoParser {

    private final String taskInfo;

    public TaskInfoParser(final String taskInfo) {
        this.taskInfo = taskInfo;
    }

    public Long getLong(final TaskInfoKey taskInfoKey) {
        final String value = getString(taskInfoKey);

        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (final NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    public Integer getInt(final TaskInfoKey taskInfoKey) {
        final String value = getString(taskInfoKey);

        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (final NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    public String getString(final TaskInfoKey taskInfoKey) {
        for (final String key : taskInfoKey.getKeys()) {
            final String searchFor = key + "=";
            int index = 0;

            while ((index = taskInfo.indexOf(searchFor, index)) != -1) {
                // Check if this is a word boundary (preceded by space, comma, or colon)
                final boolean isWordBoundary = (index == 0) ||
                                         (!Character.isLetterOrDigit(taskInfo.charAt(index - 1)) &&
                                          taskInfo.charAt(index - 1) != '_');

                if (isWordBoundary) {
                    final int startIndex = index + searchFor.length();
                    int endIndex = taskInfo.indexOf(",", startIndex);
                    if (endIndex == -1) {
                        endIndex = taskInfo.length();
                    }
                    final String value = taskInfo.substring(startIndex, endIndex).trim();
                    return value.isBlank() ? null : value;
                }
                index++;
            }
        }
        return null;
    }


    public enum TaskInfoKey {

        FEED_NAME(new String[]{"feed"}),
        FILTER_ID(new String[]{"filter id", "processor_filter_id"}),
        PIPELINE_UUID(new String[]{"pipeline uuid"}),
        STREAM_ID(new String[]{"meta_id", "stream_id"});

        private final String[] keys;

        TaskInfoKey(final String[] keys) {
            this.keys = keys;
        }

        public String[] getKeys() {
            return keys;
        }
    }

}
