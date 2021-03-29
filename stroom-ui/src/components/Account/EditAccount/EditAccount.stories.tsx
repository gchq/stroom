import { storiesOf } from "@storybook/react";
import * as React from "react";
import { EditAccountFormik } from "./EditAccount";
import { ResizableDialog } from "components/Dialog/ResizableDialog";

storiesOf("Account", module).add("Edit Account", () => (
  <ResizableDialog
    initWidth={600}
    initHeight={570}
    minWidth={284}
    minHeight={555}
    disableResize={true}
  >
    <EditAccountFormik
      initialValues={{}}
      onSubmit={() => undefined}
      onClose={() => undefined}
      onPasswordChange={() => undefined}
    />
  </ResizableDialog>
));
