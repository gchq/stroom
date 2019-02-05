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
import { Formik, FormikProps } from "formik";

import StroomDecorator from "../../lib/storybook/StroomDecorator";

import IndexVolumeGroups from "./IndexVolumeGroups";
import IndexVolumeGroupPicker from "./IndexVolumeGroupPicker";
import { addThemedStories } from "../../lib/themedStoryGenerator";

import "../../styles/main.css";
import { Switch, Route, RouteComponentProps } from "react-router";
import IndexVolumeGroupEditor from "../../components/IndexVolumeGroupEditor";

interface IndexVolumeGroupForm {
  groupName?: string;
}

const TestForm = () => (
  <Formik
    initialValues={{ groupName: undefined }}
    onSubmit={() => console.log("Do nothing on submit")}
  >
    {({ values, setFieldValue }: FormikProps<IndexVolumeGroupForm>) => (
      <form>
        <div>
          <label>Chosen Index Volume Group</label>
          <IndexVolumeGroupPicker
            onChange={e => setFieldValue("groupName", e)}
            value={values.groupName}
          />
        </div>
        <div>
          <div>Group Name: {values.groupName}</div>
        </div>
      </form>
    )}
  </Formik>
);

const IndexVolumeGroupsWithRouter = () => (
  <Switch>
    <Route
      exact
      path="/s/indexing/groups/:name"
      render={(props: RouteComponentProps<any>) => (
        <IndexVolumeGroupEditor name={props.match.params.name} />
      )}
    />
    <Route component={IndexVolumeGroups} />
  </Switch>
);

storiesOf("Sections/Index Volume Groups", module)
  .addDecorator(StroomDecorator)
  .add("Index Volume Groups", () => <IndexVolumeGroupsWithRouter />);

const stories = storiesOf("Pickers/Index Volume Group", module).addDecorator(
  StroomDecorator
);

addThemedStories(stories, <TestForm />);
