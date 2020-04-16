import { storiesOf } from "@storybook/react";
import * as React from "react";
import JsonDebug from "testing/JsonDebug";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import AppSearchBarWidget from "./AppSearchBarWidget";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";
import { useState } from "react";

const stories = storiesOf("Expression/Value Widgets/Dictionary", module);

addThemedStories(stories, () => {
  const [value, setValue] = useState<DocRefType>(undefined);
  return (
    <div>
      <AppSearchBarWidget onChange={setValue} value={value} />
      <JsonDebug value={value} />
    </div>
  );
});
