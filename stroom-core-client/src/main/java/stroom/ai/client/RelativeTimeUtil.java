/*
 * Copyright 2016-2026 Crown Copyright
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

public class RelativeTimeUtil {
    private RelativeTimeUtil() {

    }

    public static String formatRelativeTime(final long timeMs) {
        if (timeMs <= 0) {
            return "";
        }
        final long now = System.currentTimeMillis();
        final long diff = now - timeMs;
        final long seconds = diff / 1000;
        final long minutes = seconds / 60;
        final long hours = minutes / 60;
        final long days = hours / 24;

        if (days > 0) {
            return days == 1
                    ? "Yesterday"
                    : days + " days ago";
        } else if (hours > 0) {
            return hours + (hours == 1
                    ? " hour ago"
                    : " hours ago");
        } else if (minutes > 0) {
            return minutes + (minutes == 1
                    ? " minute ago"
                    : " minutes ago");
        } else {
            return "Just now";
        }
    }
}
