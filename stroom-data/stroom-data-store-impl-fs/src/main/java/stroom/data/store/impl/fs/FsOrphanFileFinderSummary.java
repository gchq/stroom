package stroom.data.store.impl.fs;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class FsOrphanFileFinderSummary {

    private final Map<SummaryLine, AtomicLong> summaryMap = new HashMap<>();

    public void addPath(final Path path) {
        final String fileName = path.getFileName().toString();
        final int index = fileName.lastIndexOf("_");
        String feedName = "";
        if (index != -1) {
            feedName = fileName.substring(0, index);
        }
        for (int i = path.getNameCount() - 5; i >= 0; i--) {
            if (i + 4 < path.getNameCount() && "store".equals(path.getName(i).toString())) {
                final String type = path.getName(i + 1).toString();
                final String year = path.getName(i + 2).toString();
                final String month = path.getName(i + 3).toString();
                final String day = path.getName(i + 4).toString();

                final String date = year + "-" + month + "-" + day;
                final SummaryLine summaryLine = new SummaryLine(type, feedName, date);
                summaryMap
                        .computeIfAbsent(summaryLine, k -> new AtomicLong())
                        .incrementAndGet();
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder summary = new StringBuilder();
        if (summaryMap.size() > 0) {
            summary.append("Summary:\n");
            final AtomicReference<String> lastStreamType = new AtomicReference<>();
            summaryMap
                    .entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey(Comparator
                            .comparing(SummaryLine::getType)
                            .thenComparing(SummaryLine::getFeed)
                            .thenComparing(SummaryLine::getDate)))
                    .forEach(entry -> {
                        String lastType = lastStreamType.get();
                        String type = entry.getKey().getType();
                        lastStreamType.set(type);

                        if (!Objects.equals(lastType, type)) {
                            summary.append(type);
                            summary.append("\n");
                        }

                        summary.append(entry.getKey().feed);
                        summary.append(" - ");
                        summary.append(entry.getKey().date);
                        summary.append(" - ");
                        summary.append(entry.getValue().get());
                        summary.append("\n");
                    });
        }
        return summary.toString();
    }

    private static class SummaryLine {

        private final String type;
        private final String feed;
        private final String date;

        public SummaryLine(final String type, final String feed, final String date) {
            this.type = type;
            this.feed = feed;
            this.date = date;
        }

        public String getType() {
            return type;
        }

        public String getFeed() {
            return feed;
        }

        public String getDate() {
            return date;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final SummaryLine that = (SummaryLine) o;
            return type.equals(that.type) && feed.equals(that.feed) && date.equals(that.date);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, feed, date);
        }
    }
}
