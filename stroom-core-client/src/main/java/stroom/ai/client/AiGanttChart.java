/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.client;

import stroom.ai.shared.MermaidGantt;
import stroom.ai.shared.MermaidGantt.Section;
import stroom.ai.shared.MermaidGantt.Task;

import java.util.ArrayList;
import java.util.List;

/// Draws a Mermaid gantt chart of the subset ([stroom.ai.shared.MermaidGanttParser]) as SVG, in the chat, with
/// Stroom's own code (no Mermaid library, nothing run): a lane per section, its tasks packed into as few rows as
/// don't overlap (as Mermaid's compact display), a bar per task and a diamond per marker, coloured by the task's
/// name (eight colours, the rest grey), a time axis below and a legend of the names. A bar shows its title in it
/// where it fits, its tooltip its title and duration, and a small diamond at each end its start and end events
/// (their names where the block gives them) and when they happened. A name can be picked out (a click on its bar
/// or legend item): its bars highlighted, the others dimmed. Every text is escaped; the colours and lines are CSS
/// classes, themed for light and dark (`ask-stroom-ai.css`). The layout's maths are here, apart from the markup, so
/// they can be tested.
///
/// A chart can be drawn zoomed to a span of its period ([AiGanttZoom]): only the tasks the span shows, cut to it,
/// with ticks for the span; and at another width (a dialog's). The SVG carries its span and plot's bounds as data
/// attributes, for [AiGanttInteraction] to map the pointer to times, and a hidden band it shows while dragging.
final class AiGanttChart {

    /// The width a chart is first drawn at in the chat, before it is drawn again at the width it has room for
    /// ([#fitWidth]).
    static final int WIDTH = 720;
    /// The least width a chart is drawn at, e.g. in a small dialog.
    static final int MIN_WIDTH = 480;
    /// The least room between two ticks, in pixels, on a chart drawn wider than [#WIDTH].
    static final int TICK_SPACING = 70;
    /// The attribute on the SVG giving the first time of the span drawn.
    static final String FROM_ATTRIBUTE = "data-from";
    /// The attribute on the SVG giving the last time of the span drawn.
    static final String TO_ATTRIBUTE = "data-to";
    /// The attribute on the SVG giving the x where the plot starts.
    static final String PLOT_LEFT_ATTRIBUTE = "data-plot-left";
    /// The attribute on the SVG giving the x where the plot ends.
    static final String PLOT_RIGHT_ATTRIBUTE = "data-plot-right";
    /// The attribute on the SVG giving the y where the plot starts.
    static final String PLOT_TOP_ATTRIBUTE = "data-plot-top";
    /// The attribute on the SVG giving the y where the plot's axis ends.
    static final String PLOT_BOTTOM_ATTRIBUTE = "data-plot-bottom";
    /// The attribute on the SVG giving the width it is drawn at.
    static final String WIDTH_ATTRIBUTE = "data-width";
    /// The attribute on the SVG giving the height it is drawn at.
    static final String HEIGHT_ATTRIBUTE = "data-height";
    /// The class of the band shown over the plot while a span is dragged across it.
    static final String BAND_CLASS = "ai-gantt-band";
    /// The most names with a colour of their own; the rest are grey.
    static final int COLOURS = 8;
    /// The name drawn grey, as the tool writes the names past its top ones.
    static final String OTHER = "Other";
    /// The most ticks on the axis.
    static final int MAX_TICKS = 8;
    /// The attribute on a bar, marker or legend item giving the name it is of, as its colour's class (one legend
    /// item, so one series, holds the grey names together).
    static final String SERIES_ATTRIBUTE = "data-series";
    /// The class of a legend item, which a click or Enter picks out.
    static final String LEGEND_ITEM_CLASS = "ai-gantt-legend-item";
    /// The class of what isn't of the name picked out.
    static final String DIMMED_CLASS = "ai-gantt--dimmed";

    private static final int PADDING = 12;
    private static final int TITLE_HEIGHT = 26;
    private static final int LANE_LABEL_WIDTH = 150;
    private static final int LANE_LABEL_CHARS = 22;
    private static final int PLOT_LEFT = PADDING + LANE_LABEL_WIDTH;
    private static final int PLOT_RIGHT_MARGIN = PADDING + 8;
    private static final int ROW_HEIGHT = 18;
    private static final int BAR_HEIGHT = 12;
    private static final int LANE_PADDING = 4;
    private static final int AXIS_HEIGHT = 24;
    private static final int LEGEND_ROW_HEIGHT = 18;
    private static final int LEGEND_SWATCH = 10;
    private static final int LEGEND_CHAR_WIDTH = 7;
    private static final int LEGEND_GAP = 18;
    private static final int LEGEND_NAME_CHARS = 30;
    private static final int MARKER_RADIUS = 5;
    // A bar's start and end markers, smaller than a milestone's.
    private static final int EDGE_RADIUS = 4;
    // A bar's title: its characters' width (at its font size, 9px), the room either side of it, and the fewest
    // characters shown before an ellipsis (fewer aren't legible as a title).
    private static final double BAR_LABEL_CHAR_WIDTH = 5.5;
    private static final double BAR_LABEL_PADDING = 3;
    private static final int MIN_BAR_LABEL_CHARS = 3;
    private static final String EDGE_TIME_FORMAT = "%d %b %Y %H:%M:%S";
    // The least gap between two tasks in a row, in pixels.
    private static final double ROW_GAP = 2;
    private static final double MIN_BAR_WIDTH = 2;
    private static final long SECOND = 1_000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = MermaidGantt.MILLIS_PER_DAY;
    // The steps the axis's ticks may be apart, the least first.
    private static final long[] STEPS = {
            SECOND, 5 * SECOND, 15 * SECOND, 30 * SECOND,
            MINUTE, 5 * MINUTE, 10 * MINUTE, 15 * MINUTE, 30 * MINUTE,
            HOUR, 2 * HOUR, 3 * HOUR, 6 * HOUR, 12 * HOUR,
            DAY, 2 * DAY, 7 * DAY, 14 * DAY, 30 * DAY, 90 * DAY, 365 * DAY};
    private static final String[] MONTHS = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final String[] WEEKDAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    // Mermaid's axis format when a chart gives none.
    private static final String DEFAULT_AXIS_FORMAT = "%Y-%m-%d";

    private AiGanttChart() {
        // Static only.
    }

    /// @param available The width, in pixels, a chart has room for in the chat; 0 or less when it isn't known
    ///                  (the chat hidden, say).
    /// @return The width to draw it at: the room it has, but at least [#MIN_WIDTH] (scaled down to fit below that,
    /// so its labels don't crowd); 0 when the room isn't known, to leave it as drawn.
    static int fitWidth(final int available) {
        return available <= 0
                ? 0
                : Math.max(MIN_WIDTH, available);
    }

    /// @param gantt A chart of the subset.
    /// @return It drawn as SVG, the whole period at [#WIDTH].
    static String svg(final MermaidGantt gantt) {
        return svg(gantt, null, WIDTH);
    }

    /// @param gantt A chart of the subset.
    /// @param view  The span to draw ([AiGanttZoom#dragRange]), or null for the whole period ([#range]).
    /// @param width The width to draw it at, at least [#MIN_WIDTH].
    /// @return It drawn as SVG: the tasks the span shows, cut to it; the legend still of all the chart's names, so
    /// the colours stay as they were.
    static String svg(final MermaidGantt gantt, final long[] view, final int width) {
        return svg(gantt, view, width, null);
    }

    /// @param gantt     A chart of the subset.
    /// @param view      The span to draw ([AiGanttZoom#dragRange]), or null for the whole period ([#range]).
    /// @param width     The width to draw it at, at least [#MIN_WIDTH].
    /// @param highlight The series picked out (a colour's class, as [#colourClass] gives it), or null for none.
    /// @return It drawn as SVG, as [#svg(MermaidGantt, long[], int)] gives it, the series picked out highlighted and
    /// the rest dimmed.
    static String svg(final MermaidGantt gantt, final long[] view, final int width, final String highlight) {
        final int drawnWidth = Math.max(MIN_WIDTH, width);
        final int plotRight = drawnWidth - PLOT_RIGHT_MARGIN;
        final List<String> names = names(gantt);
        final long[] range = view == null
                ? range(gantt)
                : view;
        final Scale scale = new Scale(range[0], range[1], PLOT_LEFT, plotRight);
        final StringBuilder body = new StringBuilder();
        int y = PADDING;
        if (gantt.getTitle() != null && !gantt.getTitle().isEmpty()) {
            body.append("<text class=\"ai-gantt-title\" x=\"").append(PADDING).append("\" y=\"").append(y + 14)
                    .append("\">").append(AiChatOverview.escape(gantt.getTitle())).append("</text>");
            y += TITLE_HEIGHT;
        }
        final int plotTop = y;
        final StringBuilder lanes = new StringBuilder();
        int laneIndex = 0;
        for (final Section section : gantt.getSections()) {
            final List<List<Task>> rows = rows(AiGanttZoom.visible(section.tasks(), scale.from(), scale.to()),
                    scale);
            final int laneHeight = Math.max(1, rows.size()) * ROW_HEIGHT + 2 * LANE_PADDING;
            appendLane(lanes, section, rows, names, scale, drawnWidth, y, laneHeight, laneIndex++, highlight);
            y += laneHeight;
        }
        if (gantt.getSections().isEmpty()) {
            lanes.append("<text class=\"ai-gantt-empty\" x=\"").append(PLOT_LEFT).append("\" y=\"")
                    .append(y + 14).append("\">No tasks</text>");
            y += ROW_HEIGHT + 2 * LANE_PADDING;
        }
        final int plotBottom = y;
        appendAxis(body, gantt.getAxisFormat(), scale, plotTop, plotBottom);
        body.append(lanes);
        // Over the lanes, hidden until a span is dragged; never in the way of the pointer.
        body.append("<rect class=\"").append(BAND_CLASS).append("\" x=\"").append(PLOT_LEFT).append("\" y=\"")
                .append(plotTop).append("\" width=\"0\" height=\"").append(plotBottom - plotTop)
                .append("\" visibility=\"hidden\"/>");
        y = plotBottom + AXIS_HEIGHT;
        final int axisBottom = y;
        y = appendLegend(body, names, y, plotRight, highlight);
        final int height = y + PADDING;
        final String label = gantt.getTitle() == null
                ? "Timeline"
                : gantt.getTitle();
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"ai-gantt-svg\" viewBox=\"0 0 " + drawnWidth + " "
               + height + "\" width=\"" + drawnWidth + "\" role=\"img\" aria-label=\"" + AiChatOverview.escape(label)
               + "\" " + FROM_ATTRIBUTE + "=\"" + scale.from() + "\" " + TO_ATTRIBUTE + "=\"" + scale.to() + "\" "
               + PLOT_LEFT_ATTRIBUTE + "=\"" + PLOT_LEFT + "\" " + PLOT_RIGHT_ATTRIBUTE + "=\"" + plotRight + "\" "
               + PLOT_TOP_ATTRIBUTE + "=\"" + plotTop + "\" " + PLOT_BOTTOM_ATTRIBUTE + "=\"" + axisBottom + "\" "
               + WIDTH_ATTRIBUTE + "=\"" + drawnWidth + "\" " + HEIGHT_ATTRIBUTE + "=\"" + height + "\">" + body
               + "</svg>";
    }

    /// @param name  A bar's title.
    /// @param width The bar's width, in pixels, as drawn.
    /// @return What of it is shown in the bar: all of it where it fits, else as many characters as fit, the last an
    /// ellipsis; null where too few would fit to read.
    static String barLabel(final String name, final double width) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        final int fit = (int) Math.floor((width - 2 * BAR_LABEL_PADDING) / BAR_LABEL_CHAR_WIDTH);
        if (name.length() <= fit) {
            return name;
        }
        return fit - 1 >= MIN_BAR_LABEL_CHARS
                ? name.substring(0, fit - 1) + "\u2026"
                : null;
    }

    /// @param current The series picked out, or null for none.
    /// @param clicked The series clicked (a bar's or a legend item's), or null for the chart's background.
    /// @return The series picked out once clicked: the one clicked, unless it is already (then none), and none for
    /// the background.
    static String toggled(final String current, final String clicked) {
        return clicked == null || clicked.equals(current)
                ? null
                : clicked;
    }

    /// @param scale Where times are drawn.
    /// @return The most ticks its axis has room for: [#MAX_TICKS] at [#WIDTH], more on a wider chart.
    static int maxTicks(final Scale scale) {
        return Math.max(MAX_TICKS, (int) ((scale.right() - scale.left()) / TICK_SPACING));
    }

    /// @param axisFormat The chart's axis format, or null for Mermaid's default.
    /// @param ticks      The axis's ticks, as [#ticks] gives them.
    /// @return The format to write them in: the chart's own, unless it would write two ticks the same (a zoomed
    /// span finer than it shows), then one fine enough for the ticks' step: seconds under a minute, hours and
    /// minutes under a day (with the day when the ticks cross midnight), else the day and month.
    static String tickFormat(final String axisFormat, final List<Long> ticks) {
        if (ticks.size() < 2 || isDistinct(ticks, axisFormat)) {
            return axisFormat;
        }
        final long step = ticks.get(1) - ticks.get(0);
        final boolean days = ticks.get(0) / DAY != ticks.get(ticks.size() - 1) / DAY;
        final String finer;
        if (step < MINUTE) {
            finer = days
                    ? "%d %b %H:%M:%S"
                    : "%H:%M:%S";
        } else if (step < DAY) {
            finer = days
                    ? "%d %b %H:%M"
                    : "%H:%M";
        } else {
            finer = "%d %b";
        }
        return finer;
    }

    /// @param gantt A chart.
    /// @return Its tasks' names, each once, in the order they first appear: the order of their colours.
    static List<String> names(final MermaidGantt gantt) {
        final List<String> names = new ArrayList<>();
        for (final Section section : gantt.getSections()) {
            for (final Task task : section.tasks()) {
                if (!names.contains(task.name())) {
                    names.add(task.name());
                }
            }
        }
        return names;
    }

    /// @param name  A task's name.
    /// @param names The chart's names, as [#names] gives them.
    /// @return The class of its colour: one of [#COLOURS] by the order the names first appear (but [#OTHER],
    /// which takes none), or grey for [#OTHER] and the names past the colours there are.
    static String colourClass(final String name, final List<String> names) {
        int index = 0;
        for (final String other : names) {
            if (other.equals(name)) {
                return OTHER.equals(name) || index >= COLOURS
                        ? "ai-gantt-other"
                        : "ai-gantt-c" + (index + 1);
            } else if (!OTHER.equals(other)) {
                index++;
            }
        }
        return "ai-gantt-other";
    }

    /// @param gantt A chart.
    /// @return The times its axis runs from and to: its tasks' earliest start and latest end, half an hour either
    /// side of a chart whose tasks are all at one time, and a day from midnight for one with no tasks.
    static long[] range(final MermaidGantt gantt) {
        long from = Long.MAX_VALUE;
        long to = Long.MIN_VALUE;
        for (final Section section : gantt.getSections()) {
            for (final Task task : section.tasks()) {
                from = Math.min(from, task.start());
                to = Math.max(to, task.end());
            }
        }
        if (from == Long.MAX_VALUE) {
            return new long[]{0, DAY};
        } else if (from == to) {
            return new long[]{from - 30 * MINUTE, to + 30 * MINUTE};
        }
        return new long[]{from, to};
    }

    /// @param from     The axis's first time.
    /// @param to       Its last.
    /// @param maxTicks The most ticks.
    /// @return The ticks: whole multiples of the least step that gives no more than `maxTicks` between the two
    /// (on the wall clock, so days start at midnight).
    static List<Long> ticks(final long from, final long to, final int maxTicks) {
        long step = STEPS[STEPS.length - 1];
        for (final long candidate : STEPS) {
            if ((to - from) / candidate < maxTicks) {
                step = candidate;
                break;
            }
        }
        final List<Long> ticks = new ArrayList<>();
        // The least multiple of the step at or after the first time.
        long first = from / step * step;
        if (first < from) {
            first += step;
        }
        for (long tick = first; tick <= to; tick += step) {
            ticks.add(tick);
        }
        return ticks;
    }

    /// @param tasks A section's tasks.
    /// @param scale Where times are drawn.
    /// @return The tasks in rows, each in the first row it doesn't overlap (a marker taking its diamond's width),
    /// in order of start.
    static List<List<Task>> rows(final List<Task> tasks, final Scale scale) {
        final List<Task> sorted = new ArrayList<>(tasks);
        sorted.sort((a, b) -> Long.compare(a.start(), b.start()));
        final List<List<Task>> rows = new ArrayList<>();
        final List<Double> rowEnds = new ArrayList<>();
        for (final Task task : sorted) {
            final double left = left(task, scale);
            final double right = right(task, scale);
            int row = 0;
            while (row < rows.size() && rowEnds.get(row) + ROW_GAP > left) {
                row++;
            }
            if (row == rows.size()) {
                rows.add(new ArrayList<>());
                rowEnds.add(right);
            } else {
                rowEnds.set(row, right);
            }
            rows.get(row).add(task);
        }
        return rows;
    }

    /// @param wallClock A time, as [MermaidGantt#wallClock] gives it.
    /// @param format    A d3 time format of the subset (`%Y`, `%y`, `%m`, `%d`, `%e`, `%b`, `%a`, `%H`, `%M`,
    ///                  `%S`), or null for Mermaid's default.
    /// @return The time in the format.
    static String format(final long wallClock, final String format) {
        final String pattern = format == null
                ? DEFAULT_AXIS_FORMAT
                : format;
        final int[] fields = MermaidGantt.fieldsOf(wallClock);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            final char c = pattern.charAt(i);
            if (c == '%' && i + 1 < pattern.length()) {
                i++;
                switch (pattern.charAt(i)) {
                    case 'Y' -> sb.append(fields[0]);
                    case 'y' -> sb.append(twoDigits(fields[0] % 100));
                    case 'm' -> sb.append(twoDigits(fields[1]));
                    case 'd' -> sb.append(twoDigits(fields[2]));
                    case 'e' -> sb.append(fields[2]);
                    case 'b' -> sb.append(MONTHS[fields[1] - 1]);
                    case 'a' -> sb.append(WEEKDAYS[fields[7]]);
                    case 'H' -> sb.append(twoDigits(fields[3]));
                    case 'M' -> sb.append(twoDigits(fields[4]));
                    case 'S' -> sb.append(twoDigits(fields[5]));
                    default -> sb.append('%').append(pattern.charAt(i));
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /// @param millis A length of time.
    /// @return It for people, e.g. "45 s", "12 min", "1 h 30 min".
    static String duration(final long millis) {
        final long seconds = millis / SECOND;
        if (seconds < 60) {
            return seconds + " s";
        } else if (seconds < 3_600) {
            return seconds / 60 + " min";
        }
        final long minutes = seconds / 60 % 60;
        return seconds / 3_600 + " h" + (minutes == 0
                ? ""
                : " " + minutes + " min");
    }

    private static void appendLane(final StringBuilder sb,
                                   final Section section,
                                   final List<List<Task>> rows,
                                   final List<String> names,
                                   final Scale scale,
                                   final int width,
                                   final int top,
                                   final int height,
                                   final int index,
                                   final String highlight) {
        sb.append("<g class=\"ai-gantt-lane").append(index % 2 == 1
                ? " ai-gantt-lane--odd"
                : "").append("\">");
        sb.append("<rect class=\"ai-gantt-lane-back\" x=\"").append(PADDING).append("\" y=\"").append(top)
                .append("\" width=\"").append(width - 2 * PADDING).append("\" height=\"").append(height)
                .append("\"/>");
        sb.append("<text class=\"ai-gantt-lane-label\" x=\"").append(PADDING + 6).append("\" y=\"")
                .append(top + height / 2 + 4).append("\">")
                .append(AiChatOverview.escape(shortened(section.name(), LANE_LABEL_CHARS)));
        if (section.name().length() > LANE_LABEL_CHARS) {
            sb.append("<title>").append(AiChatOverview.escape(section.name())).append("</title>");
        }
        sb.append("</text>");
        for (int row = 0; row < rows.size(); row++) {
            final int centre = top + LANE_PADDING + row * ROW_HEIGHT + ROW_HEIGHT / 2;
            for (final Task task : rows.get(row)) {
                appendTask(sb, task, names, scale, centre, highlight);
            }
        }
        sb.append("</g>");
    }

    private static void appendTask(final StringBuilder sb,
                                   final Task task,
                                   final List<String> names,
                                   final Scale scale,
                                   final int centre,
                                   final String highlight) {
        final String series = colourClass(task.name(), names);
        final String classes = series
                               + (task.tags().contains("crit")
                ? " ai-gantt--crit"
                : "")
                               + (task.tags().contains("done")
                ? " ai-gantt--done"
                : "")
                               + dimmed(series, highlight);
        final String seriesAttribute = " " + SERIES_ATTRIBUTE + "=\"" + series + "\"";
        if (task.isMarker()) {
            final String tooltip = "<title>" + AiChatOverview.escape(task.name() + ": "
                                                                     + format(task.start(), "%d %b %H:%M:%S"))
                                   + "</title>";
            sb.append("<path class=\"ai-gantt-marker ").append(classes).append('"').append(seriesAttribute)
                    .append(" d=\"").append(diamond(scale.position(task.start()), centre, MARKER_RADIUS))
                    .append("\">").append(tooltip).append("</path>");
            return;
        }
        // A bar's tooltip: its title and how long it lasted (its times are its markers').
        final String tooltip = "<title>" + AiChatOverview.escape(task.name() + " (" + duration(task.end()
                                                                                                - task.start()) + ")")
                               + "</title>";
        final double left = left(task, scale);
        final double barWidth = right(task, scale) - left;
        final int top = centre - BAR_HEIGHT / 2;
        sb.append("<rect class=\"ai-gantt-bar ").append(classes).append('"').append(seriesAttribute)
                .append(" x=\"").append(number(left)).append("\" y=\"").append(top).append("\" width=\"")
                .append(number(barWidth)).append("\" height=\"").append(BAR_HEIGHT).append("\" rx=\"2\">")
                .append(tooltip).append("</rect>");
        final String label = barLabel(task.name(), barWidth);
        if (label != null) {
            // In a box the bar's size, so it never runs past it; the pointer goes through it to the bar.
            sb.append("<svg class=\"ai-gantt-bar-label-box").append(dimmed(series, highlight)).append("\" x=\"")
                    .append(number(left)).append("\" y=\"").append(top).append("\" width=\"")
                    .append(number(barWidth)).append("\" height=\"")
                    .append(BAR_HEIGHT).append("\"><text class=\"ai-gantt-bar-label\" x=\"")
                    .append(number(BAR_LABEL_PADDING)).append("\" y=\"").append(BAR_HEIGHT - 3).append("\">")
                    .append(AiChatOverview.escape(label)).append("</text></svg>");
        }
        // Its start and end, where the span drawn shows them: a diamond each, naming its event and when it was.
        if (task.start() >= scale.from()) {
            appendEdge(sb, scale.position(task.start()), centre, task.startEvent(), "Start", task.start(), series,
                    highlight);
        }
        if (task.end() <= scale.to()) {
            appendEdge(sb, scale.position(task.end()), centre, task.endEvent(), "End", task.end(), series,
                    highlight);
        }
    }

    private static void appendEdge(final StringBuilder sb,
                                   final double x,
                                   final int centre,
                                   final String event,
                                   final String otherwise,
                                   final long time,
                                   final String series,
                                   final String highlight) {
        final String named = event == null
                ? otherwise
                : event;
        // Of the page's text colour, not the bar's, so it shows on the bar.
        sb.append("<path class=\"ai-gantt-edge").append(dimmed(series, highlight)).append("\" ")
                .append(SERIES_ATTRIBUTE).append("=\"").append(series).append("\" d=\"")
                .append(diamond(x, centre, EDGE_RADIUS)).append("\"><title>")
                .append(AiChatOverview.escape(named + ": " + format(time, EDGE_TIME_FORMAT)))
                .append("</title></path>");
    }

    // A diamond's path about a point.
    private static String diamond(final double x, final int centre, final int radius) {
        return "M" + number(x) + "," + (centre - radius) + " L" + number(x + radius) + "," + centre + " L"
               + number(x) + "," + (centre + radius) + " L" + number(x - radius) + "," + centre + " Z";
    }

    // The class of what isn't of the series picked out; nothing with none picked out.
    private static String dimmed(final String series, final String highlight) {
        return highlight == null || highlight.equals(series)
                ? ""
                : " " + DIMMED_CLASS;
    }

    private static void appendAxis(final StringBuilder sb,
                                   final String axisFormat,
                                   final Scale scale,
                                   final int top,
                                   final int bottom) {
        sb.append("<g class=\"ai-gantt-axis\">");
        final List<Long> ticks = ticks(scale.from(), scale.to(), maxTicks(scale));
        final String format = tickFormat(axisFormat, ticks);
        for (final long tick : ticks) {
            final String x = number(scale.position(tick));
            sb.append("<line class=\"ai-gantt-grid\" x1=\"").append(x).append("\" y1=\"").append(top)
                    .append("\" x2=\"").append(x).append("\" y2=\"").append(bottom).append("\"/>");
            sb.append("<text class=\"ai-gantt-tick\" x=\"").append(x).append("\" y=\"").append(bottom + 16)
                    .append("\" text-anchor=\"middle\">").append(AiChatOverview.escape(format(tick, format)))
                    .append("</text>");
        }
        sb.append("<line class=\"ai-gantt-baseline\" x1=\"").append(PLOT_LEFT).append("\" y1=\"").append(bottom)
                .append("\" x2=\"").append(number(scale.right())).append("\" y2=\"").append(bottom).append("\"/>");
        sb.append("</g>");
    }

    // The legend: a swatch and name for each name with a colour, then the rest as one grey entry, wrapped to the
    // chart's width. Returns the y below it.
    private static int appendLegend(final StringBuilder sb,
                                    final List<String> names,
                                    final int top,
                                    final int plotRight,
                                    final String highlight) {
        final List<String> entries = new ArrayList<>();
        final List<String> classes = new ArrayList<>();
        int others = 0;
        boolean otherShown = false;
        for (final String name : names) {
            final String colour = colourClass(name, names);
            if (!"ai-gantt-other".equals(colour)) {
                entries.add(name);
                classes.add(colour);
            } else if (OTHER.equals(name)) {
                otherShown = true;
            } else {
                others++;
            }
        }
        if (otherShown || others > 0) {
            entries.add(otherShown && others == 0
                    ? OTHER
                    : (otherShown
                            ? OTHER + " and "
                            : "") + others + " more");
            classes.add("ai-gantt-other");
        }
        if (entries.isEmpty()) {
            return top;
        }
        sb.append("<g class=\"ai-gantt-legend\">");
        int x = PLOT_LEFT;
        int y = top + 4;
        for (int i = 0; i < entries.size(); i++) {
            final String text = shortened(entries.get(i), LEGEND_NAME_CHARS);
            final int width = LEGEND_SWATCH + 6 + text.length() * LEGEND_CHAR_WIDTH;
            if (x > PLOT_LEFT && x + width > plotRight) {
                x = PLOT_LEFT;
                y += LEGEND_ROW_HEIGHT;
            }
            // A button of its own: a click, or Enter or Space when focused, picks its name out.
            final String series = classes.get(i);
            sb.append("<g class=\"").append(LEGEND_ITEM_CLASS).append(dimmed(series, highlight)).append("\" ")
                    .append(SERIES_ATTRIBUTE).append("=\"").append(series).append("\" tabindex=\"0\" "
                                                                                + "role=\"button\" aria-pressed=\"")
                    .append(series.equals(highlight)).append("\" aria-label=\"")
                    .append(AiChatOverview.escape("Pick out " + entries.get(i))).append("\">");
            sb.append("<rect class=\"ai-gantt-swatch ").append(series).append("\" x=\"").append(x)
                    .append("\" y=\"").append(y).append("\" width=\"").append(LEGEND_SWATCH).append("\" height=\"")
                    .append(LEGEND_SWATCH).append("\" rx=\"2\"/>");
            sb.append("<text class=\"ai-gantt-legend-text\" x=\"").append(x + LEGEND_SWATCH + 6).append("\" y=\"")
                    .append(y + LEGEND_SWATCH - 1).append("\">").append(AiChatOverview.escape(text))
                    .append("</text></g>");
            x += width + LEGEND_GAP;
        }
        sb.append("</g>");
        return y + LEGEND_ROW_HEIGHT;
    }

    // A bar's ends are held to the span drawn (AiGanttZoom.clip), so a zoomed chart cuts it at the plot's edges.
    private static double left(final Task task, final Scale scale) {
        return task.isMarker()
                ? scale.position(task.start()) - MARKER_RADIUS
                : scale.position(AiGanttZoom.clip(task, scale.from(), scale.to())[0]);
    }

    private static double right(final Task task, final Scale scale) {
        if (task.isMarker()) {
            return scale.position(task.start()) + MARKER_RADIUS;
        }
        final long[] clipped = AiGanttZoom.clip(task, scale.from(), scale.to());
        return Math.max(scale.position(clipped[1]), scale.position(clipped[0]) + MIN_BAR_WIDTH);
    }

    private static boolean isDistinct(final List<Long> ticks, final String format) {
        final List<String> labels = new ArrayList<>();
        for (final long tick : ticks) {
            final String label = format(tick, format);
            if (labels.contains(label)) {
                return false;
            }
            labels.add(label);
        }
        return true;
    }

    private static String shortened(final String text, final int maxChars) {
        return text.length() > maxChars
                ? text.substring(0, maxChars - 1) + "…"
                : text;
    }

    private static String twoDigits(final int value) {
        return value < 10
                ? "0" + value
                : String.valueOf(value);
    }

    // A coordinate to a tenth of a pixel, as SVG takes it.
    private static String number(final double value) {
        final long tenths = Math.round(value * 10);
        final long whole = tenths / 10;
        final long tenth = Math.abs(tenths % 10);
        return tenth == 0
                ? String.valueOf(whole)
                : (tenths < 0 && whole == 0
                        ? "-"
                        : "") + whole + "." + tenth;
    }


    // --------------------------------------------------------------------------------


    /// Where times are drawn across the chart.
    ///
    /// @param from  The axis's first time.
    /// @param to    Its last, after `from`.
    /// @param left  The x of `from`.
    /// @param right The x of `to`.
    record Scale(long from, long to, double left, double right) {

        /// @param time A time.
        /// @return Its x.
        double position(final long time) {
            return left + (right - left) * (time - from) / (double) (to - from);
        }
    }
}
