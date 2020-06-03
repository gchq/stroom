import * as React from "react";
import { storiesOf } from "@storybook/react";
import fullTestData from "testing/data";
import DocRefInfoForm from "./DocRefInfoForm";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";

const testFolder1 = fullTestData.documentTree.children![0];

const TestHarness: React.FunctionComponent = () => {
  return <DocRefInfoForm docRef={testFolder1} />;
};

const stories = storiesOf("Doc Ref/Info", module);
addThemedStories(stories, "Form", () => <TestHarness />);
