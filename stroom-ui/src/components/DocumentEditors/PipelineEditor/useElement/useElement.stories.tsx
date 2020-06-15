import * as React from "react";

import { storiesOf } from "@storybook/react";
import JsonDebug from "testing/JsonDebug";
import useElement from "./useElement";

import { fullTestData } from "testing/data";

interface Props {
  type: string;
}

const TestHarness: React.FunctionComponent<Props> = ({ type }) => {
  const element = useElement(type);

  return <JsonDebug value={element} />;
};

const stories = storiesOf("Document Editors/Pipeline/useElement", module);

fullTestData.elements
  .map((e) => e.type)
  .forEach((eType) => stories.add(eType, () => <TestHarness type={eType} />));
