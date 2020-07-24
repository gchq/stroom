import * as React from "react";
import { storiesOf } from "@storybook/react";
import fullTestData from "testing/data";
import { FeedDoc } from "components/DocumentEditors/useDocumentApi/types/feed";
import ActiveTasks from "./ActiveTasks";

const feed: FeedDoc = fullTestData.documents.Feed[0];

storiesOf("Document Editors/Feed", module).add("Active Tasks", () => (
  <ActiveTasks feedUuid={feed.uuid} />
));
