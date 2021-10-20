import { storiesOf } from "@storybook/react";
import * as React from "react";
import { AccountListDialog } from "./AccountListDialog";

import fullTestData from "testing/data";
import useColumns from "./useColumns";

const stories = storiesOf("Account", module);
stories.add("List", () => {
  const columns = useColumns();

  return (
    <AccountListDialog
      itemManagerProps={{
        tableProps: {
          columns: columns,
          data: fullTestData.users,
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
        keyExtractor: (account) => account.id.toString(),
      }}
      onClose={() => undefined}
    />
  );
});
