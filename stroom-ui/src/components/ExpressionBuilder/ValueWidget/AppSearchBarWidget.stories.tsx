import { storiesOf } from "@storybook/react";
import * as React from "react";
import JsonDebug from "testing/JsonDebug";
import AppSearchBarWidget from "./AppSearchBarWidget";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";
import { useState } from "react";

storiesOf("Expression/Value Widgets", module).add("Dictionary", () => {
  const [value, setValue] = useState<DocRefType>(undefined);
  return (
    <div>
      <AppSearchBarWidget onChange={setValue} value={value} />
      <JsonDebug value={value} />
    </div>
  );
});
