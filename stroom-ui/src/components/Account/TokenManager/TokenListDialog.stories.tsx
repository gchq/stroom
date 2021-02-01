import { storiesOf } from "@storybook/react";
import * as React from "react";
import { TokenListDialog } from "./TokenListDialog";

import fullTestData from "testing/data";
import useTokenManager from "./useTokenManager";

const stories = storiesOf("Token", module);
stories.add("List", () => {
  const { columns } = useTokenManager();

  return (
    <TokenListDialog
      itemManagerProps={{
        tableProps: {
          columns: columns,
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
      }}
      onClose={() => undefined}
    />
  );
});
