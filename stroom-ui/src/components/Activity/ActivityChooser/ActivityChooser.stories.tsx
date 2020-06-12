/*
 * Copyright 2018 Crown Copyright
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

import * as React from "react";

import { storiesOf } from "@storybook/react";
import { Switch, Route } from "react-router";

import ActivityChooser from "./ActivityChooser";

// import IndexVolumeEditor from "./ActivityChooser";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";

const TestHarness = () => (
  <Switch>
    {/* <Route
      exact
      path="/s/indexing/volumes/:id"
      render={(props: RouteComponentProps<any>) => (
        <IndexVolumeEditor volumeId={props.match.params.id} />
      )}
    /> */}
    <Route component={ActivityChooser} />
  </Switch>
);

const stories = storiesOf("Sections/Activity", module);

addThemedStories(stories, "Chooser", () => <TestHarness />);
