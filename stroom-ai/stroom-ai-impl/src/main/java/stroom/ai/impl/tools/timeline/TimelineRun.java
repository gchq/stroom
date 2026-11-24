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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.time.ToolTimeZone;
import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Characterised;
import stroom.ai.impl.tools.timeline.PairInference.Candidate;
import stroom.ai.impl.tools.timeline.TimelineReader.Read;
import stroom.ai.impl.tools.timeline.TimelineReader.Source;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.ai.impl.tools.timeline.TimelineSessions.Timeline;
import stroom.ai.impl.tools.timeline.TimelineSpans.Built;
import stroom.query.api.TimeRange;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/// Everything one run of `activity_timeline` worked out, for its tables ([TimelineTables]) and its result
/// ([TimelineResult]).
///
/// @param entity         The user.
/// @param period         The period read.
/// @param capMs          The idle cap.
/// @param sources        The sources read.
/// @param notes          Each index considered: its user field and where it came from, or why it was left out.
/// @param chosen         How the sources were chosen.
/// @param read           What was read.
/// @param classification How the automatic sources' events were classified.
/// @param xml            What of their Events XML was read.
/// @param timeline       The focus sessions.
/// @param built          The spans of every kind, and the actions' attachment.
/// @param byId           Every event, by its id.
/// @param borderline     The borderline pairs, listed, not used.
/// @param characterised  The activities, or null.
/// @param view           The view asked for.
/// @param lanes          The lanes asked for, or null for the view's default.
/// @param learned        What was learned for the skill, and the offer to record it.
/// @param zone           The user's zone.
/// @param ranges         Where the period has no events, each source's first and last at any time; else empty.
/// @param targets        What was done to the person (`includeTargets`), kept apart from their activity; null if
///                       not asked.
/// @param hostLanes      What the lanes by host are: the field holding the host, described
///                       ([TimelineLaneTitles#hosts]); null for lanes by source.
record TimelineRun(String entity,
                   Period period,
                   long capMs,
                   List<Source> sources,
                   List<SourceNote> notes,
                   String chosen,
                   Read read,
                   TimelineClassification.Result classification,
                   XmlRead xml,
                   Timeline timeline,
                   Built built,
                   Map<String, Event> byId,
                   List<Candidate> borderline,
                   Characterised characterised,
                   View view,
                   String lanes,
                   TimelineLearning.Learned learned,
                   ToolTimeZone zone,
                   List<EventRange> ranges,
                   TimelineTargets.Asked targets,
                   String hostLanes) {

    /// @param source A source, by its place.
    /// @return Its name.
    String sourceName(final int source) {
        return source >= 0 && source < sources.size()
                ? sources.get(source).name()
                : "source " + (source + 1);
    }

    /// @return The title of the spans' lanes ([TimelineLaneTitles#spans]).
    String spanLaneTitle() {
        return TimelineLaneTitles.spans(lanes, hostLanes);
    }

    /// @return The title of the activities' lanes ([TimelineLaneTitles#activities]).
    String activityLaneTitle() {
        return TimelineLaneTitles.activities(lanes, zone.id());
    }

    /// @return The sources' names, by their places.
    List<String> names() {
        return sources.stream().map(Source::name).toList();
    }


    // --------------------------------------------------------------------------------


    /// The period read.
    ///
    /// @param fromMs    Its start.
    /// @param toMs      Its end.
    /// @param timeRange It as a time range of instants.
    record Period(long fromMs, long toMs, TimeRange timeRange) {

    }


    // --------------------------------------------------------------------------------


    /// One index considered.
    ///
    /// @param name      Its name in the result.
    /// @param index     The index's name.
    /// @param userField The field holding the user, or null.
    /// @param userFrom  Where that came from, or null.
    /// @param transform A transform tried as none found the user, or null.
    /// @param leftOut   Why it was left out, or null if it was read.
    /// @param by        Whether its user field holds the person id or the logon id, or null if neither was found.
    /// @param readAs    Where nothing matched the one user as given, so their other written forms were read (the
    ///                  domain and case set aside): the forms the source's first events hold; empty otherwise.
    /// @param otherForms Where the one user was found as given: the other written forms the index also holds,
    ///                   offered but not read (another domain's account may be someone else's); empty otherwise.
    record SourceNote(String name, String index, String userField, String userFrom, String transform,
                      String leftOut, PersonScope.By by, List<String> readAs, List<String> otherForms) {

    }


    // --------------------------------------------------------------------------------


    /// What of the automatic sources' Events XML was read.
    ///
    /// @param wanted     The events with a StreamId and EventId, of the automatic sources.
    /// @param read       Those whose XML was read.
    /// @param missing    Those whose stream or event is gone (retention), or isn't Events.
    /// @param unreadable Those larger than the limit.
    /// @param pastCap    Those past the cap, not read.
    /// @param notAllowed Those not read as the user may not view data.
    /// @param cap        The cap.
    record XmlRead(int wanted, int read, int missing, int unreadable, int pastCap, int notAllowed, int cap) {

        /// Nothing to read.
        static final XmlRead NONE = new XmlRead(0, 0, 0, 0, 0, 0, 0);
    }


    // --------------------------------------------------------------------------------


    /// A view: which level the timeline is drawn at.
    enum View {
        /// What the user was working on: an activity row a day (or an activity a lane).
        ACTIVITIES,
        /// The spans: lanes by host (or source).
        DETAIL,
        /// Both.
        BOTH;

        /// @return Its name in a call.
        String argName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }


    // --------------------------------------------------------------------------------


    /// A source's first and last events of whom the timeline is of, at any time: given where the period has none.
    ///
    /// @param source Its name.
    /// @param first  The first event's time, or null if there is none.
    /// @param last   The last event's time, or null.
    /// @param events How many events there are, or -1 if that isn't known.
    /// @param why    Why it isn't known, or null.
    record EventRange(String source, String first, String last, long events, String why) {

    }
}
