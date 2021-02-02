import { storiesOf } from "@storybook/react";
import * as React from "react";
import { ResizableDialog } from "components/Dialog/ResizableDialog";
import { CreateTokenFormik } from "./CreateToken";

storiesOf("Token", module).add("Create Token", () => (
  <ResizableDialog
    initWidth={348}
    initHeight={319}
    minWidth={348}
    minHeight={319}
    disableResize={true}
  >
    <CreateTokenFormik
      initialValues={{
        expiresOnMs: new Date().getTime(),
        userId: undefined,
      }}
      onSubmit={() => undefined}
      onClose={() => undefined}
    />
  </ResizableDialog>
));
