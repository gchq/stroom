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
import stroom.gwt.workbench.framework.client.play.PlayException;
import stroom.gwt.workbench.framework.client.play.Spies;
import stroom.gwt.workbench.framework.client.play.Spy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStoryContext {

    @Test
    void testFn_sharesSpiesWithThePlayFunction() {
        final Story story = story();
        final StoryContext context = new StoryContext(story, Args.of("text", "Hi"));
        final Spy spy = context.fn("onTestStoryContext");

        assertThat(spy.getName()).isEqualTo("onTestStoryContext");
        assertThat(context.fn("onTestStoryContext")).isSameAs(spy);
        // Not logged, as logging needs a browser
        spy.logTo(null).call("a");
        assertThat(Spies.get("onTestStoryContext").getCalls()).containsExactly(List.of("a"));
        assertThat(context.getStory()).isSameAs(story);
        assertThat(context.getArgs().getString("text", null)).isEqualTo("Hi");
        // The default theme unless one is given
        assertThat(context.getTheme()).isEqualTo(StoryTheme.DEFAULT);
        assertThat(new StoryContext(story, Args.empty(), StoryTheme.LIGHT).getTheme()).isEqualTo(StoryTheme.LIGHT);
    }

    @Test
    void testNewContextStartsANewRendering() {
        final Spy spy = new StoryContext(story(), Args.empty()).fn("onTestStoryContextClear").logTo(null);
        spy.call("a");
        final Spy playSpy = Spies.get("onTestStoryContextClear");
        assertThat(playSpy.getCallCount()).isOne();

        // As happens when the story is rendered again, e.g. on a rewind
        final Spy next = new StoryContext(story(), Args.empty()).fn("onTestStoryContextClear").logTo(null);

        assertThat(playSpy.getCalls()).isEmpty();
        // The old rendering's spy no longer records
        spy.call("late");
        assertThat(playSpy.getCalls()).isEmpty();
        next.call("b");
        assertThat(playSpy.getCalls()).containsExactly(List.of("b"));
    }

    @Test
    void testStaleContextCantRegisterSpiesInTheNewRendering() {
        // Regression: a late callback of an old rendering calling context.fn("onX").call()
        // created and recorded the spy in the new rendering, so toHaveBeenCalled() passed wrongly
        final StoryContext old = new StoryContext(story(), Args.empty());
        final StoryContext current = new StoryContext(story(), Args.empty());

        final Spy stale = old.fn("onTestStoryContextStale");
        stale.call("late");

        assertThat(stale).isNotSameAs(current.fn("onTestStoryContextStale").logTo(null));
        assertThat(stale.getCalls()).isEmpty();
        assertThat(Spies.get("onTestStoryContextStale").getCalls()).isEmpty();
        // A stale context never registers the spy, so a misspelt name still fails
        old.fn("onTestStoryContextOnlyStale").call();
        assertThatThrownBy(() -> Spies.get("onTestStoryContextOnlyStale").getCalls())
                .isInstanceOf(PlayException.class);
    }

    private static Story story() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Test/Context").story("Default", context -> null);
        return registry.getStories().get(0);
    }
}
