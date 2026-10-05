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


package stroom.gwt.workbench.framework.client.story;

import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.play.Spies;
import stroom.gwt.workbench.framework.client.play.Spy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestStoryContext {

    @Test
    void testFn_sharesSpiesWithThePlayFunction() {
        final Story story = story();
        final StoryContext context = new StoryContext(story, Args.of("text", "Hi"));
        final Spy spy = context.fn("onTestStoryContext");

        assertThat(spy.getName()).isEqualTo("onTestStoryContext");
        assertThat(Spies.get("onTestStoryContext")).isSameAs(spy);
        assertThat(context.fn("onTestStoryContext")).isSameAs(spy);
        assertThat(context.getStory()).isSameAs(story);
        assertThat(context.getArgs().getString("text", null)).isEqualTo("Hi");
    }

    @Test
    void testNewContextClearsSpies() {
        // Not logged, as logging needs a browser
        final Spy spy = Spies.get("onTestStoryContextClear").logTo(null);
        spy.call("a");

        // As happens when the story is rendered again, e.g. on a rewind
        new StoryContext(story(), Args.empty());

        assertThat(spy.getCalls()).isEmpty();
        assertThat(Spies.get("onTestStoryContextClear")).isSameAs(spy);
    }

    private static Story story() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Test/Context").story("Default", context -> null);
        return registry.getStories().get(0);
    }
}
