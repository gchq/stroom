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
import * as uuidv4 from "uuid/v4";
import { storiesOf } from "@storybook/react";
import { Color } from "csstype";

import HorizontalMainDetails from "./HorizontalMainDetails";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import { loremIpsum } from "lorem-ipsum";

const EnabledCheckbox = () => (
  <label>
    <input type="checkbox" name="checkbox" value="value" />
    &nbsp;Enabled?
  </label>
);

interface Props {
  storageKey: string;
  title: string;
  detailContent: React.ReactNode;
}
const TestHarness: React.FunctionComponent<Props> = ({
  storageKey,
  title,
  detailContent,
}) => {
  const [isOpen, setIsOpen] = React.useState<boolean>(false);

  const onOpen = React.useCallback(() => setIsOpen(true), [setIsOpen]);
  const onClose = React.useCallback(() => setIsOpen(false), [setIsOpen]);

  return (
    <HorizontalMainDetails
      storageKey={storageKey}
      title={title}
      isOpen={isOpen}
      onClose={onClose}
      mainContent={
        <div>
          <button onClick={onOpen}>Open</button>
        </div>
      }
      detailContent={detailContent}
      headerMenuItems={<EnabledCheckbox />}
    />
  );
};

storiesOf("General Purpose/Horizontal Main Details", module)
  .add("div content", () => (
    <TestHarness
      storageKey="someTitle"
      title="Some title"
      detailContent={<div>{loremIpsum({ count: 100, units: "words" })}</div>}
    />
  ))
  .add("long title", () => (
    <TestHarness
      storageKey="longLongTitle"
      title="Some very, very long title"
      detailContent={<div>{loremIpsum({ count: 100, units: "words" })}</div>}
    />
  ))
  .add("long title with adjusted columns", () => (
    <TestHarness
      storageKey="longLongTitleAdjustColumns"
      title="Some very, very long title"
      detailContent={<div>{loremIpsum({ count: 100, units: "words" })}</div>}
      //titleColumns="8"
      //menuColumns="8"
    />
  ))
  .add("With different sized header", () => (
    <TestHarness
      storageKey="differentHeader"
      title="A smaller header"
      detailContent={<div>{loremIpsum({ count: 100, units: "words" })}</div>}

      //headerSize="h4"
    />
  ))
  .add("with lots of content", () => (
    <TestHarness
      storageKey="lotsContent"
      title="A smaller header"
      detailContent={<div>{loremIpsum({ count: 6000, units: "words" })}</div>}
      //headerSize="h4"
    />
  ));

interface PanelPartProps {
  title: string;
  backgroundColor: Color;
}
const PanelPart: React.FunctionComponent<PanelPartProps> = ({
  title,
  backgroundColor,
  children,
}) => {
  const uuid = React.useMemo(() => uuidv4(), []);

  return (
    <div style={{ backgroundColor }}>
      <h1>{title}</h1>
      <p>Generates a Random UUID When Mounted</p>
      <p>
        <strong>{uuid}</strong>
      </p>
      {children}
    </div>
  );
};

const PanelTest = () => {
  const [isOpen, setIsOpen] = React.useState<boolean>(false);

  const onOpen = React.useCallback(() => setIsOpen(true), [setIsOpen]);
  const onClose = React.useCallback(() => setIsOpen(false), [setIsOpen]);

  return (
    <HorizontalMainDetails
      storageKey="dev-sandbox"
      title="Dev Sandbox"
      isOpen={isOpen}
      onClose={onClose}
      mainContent={
        <PanelPart title="First" backgroundColor="pink">
          <button onClick={onOpen}>Open Second</button>
        </PanelPart>
      }
      detailContent={<PanelPart title="Second" backgroundColor="lightblue" />}
    />
  );
};

const stories = storiesOf(
  "General Purpose/Horizontal Main Details",
  module,
);
addThemedStories(stories, "Mounting Issue", () => <PanelTest />);
