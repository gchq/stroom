import * as React from "react";

import { storiesOf } from "@storybook/react";

import fullTestData from "testing/data";
import { docRefEditorClasses } from "./types";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import SwitchedDocRefEditor from ".";

Object.keys(docRefEditorClasses).forEach(docRefType => {
  const stories = storiesOf(`Document Editors/${docRefType}`, module);

  // 'System' will not have a list in documents.
  if (
    !!fullTestData.documents[docRefType] &&
    fullTestData.documents[docRefType].length > 0
  ) {
    let uuid: string = fullTestData.documents[docRefType][0].uuid;
    addThemedStories(stories, () => <SwitchedDocRefEditor docRefUuid={uuid} />);
  }
});
