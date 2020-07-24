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
import { Switch, Route, RouteComponentProps } from "react-router";

import fullTestData from "testing/data";

import DocumentPermissionEditor from "./DocumentPermissionEditor";
import DocumentPermissionForUserEditor from "../DocumentPermissionForUserEditor";

const testDocRef = fullTestData.documentTree.children![0].children![0];

interface Props {
  docRefUuid: string;
}

const TestHarness: React.FunctionComponent<Props> = ({ docRefUuid }) => (
  <Switch>
    <Route
      exact
      path="/s/authorisationManager/document/:docRefUuid/:userUuid"
      render={({
        match: {
          params: { userUuid, docRefUuid },
        },
      }: RouteComponentProps<any>) => (
        <DocumentPermissionForUserEditor
          docRefUuid={docRefUuid}
          userUuid={userUuid}
        />
      )}
    />
    <Route
      render={() => <DocumentPermissionEditor docRefUuid={docRefUuid} />}
    />
  </Switch>
);

storiesOf(
  "Sections/Authorisation Manager",
  module,
).add("Document Permission Editor", () => (
  <TestHarness docRefUuid={testDocRef.uuid} />
));
