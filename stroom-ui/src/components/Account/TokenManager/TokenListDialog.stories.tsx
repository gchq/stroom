import { storiesOf } from "@storybook/react";
import * as React from "react";
import { TokenListDialog } from "./TokenListDialog";

import fullTestData from "testing/data";
import useColumns from "./useColumns";

const stories = storiesOf("Token", module);
stories.add("List", () => {
  return (
    <TokenListDialog
      itemManagerProps={{
        tableProps: {
          columns: useColumns(),
          data: fullTestData.tokens,
        },
        actions: {
          onCreate: () => undefined,
          onEdit: () => undefined,
          onRemove: () => undefined,
        },
        quickFilterProps: {},
        pagerProps: {
          page: {
            from: 0,
            to: 0,
            of: 0,
          },
        },
        keyExtractor: (token) => token.id.toString(),
      }}
      onClose={() => undefined}
    />
  );
});
