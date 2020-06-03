import * as React from "react";
import { storiesOf } from "@storybook/react";
import fullTestData from "testing/data";
import { FeedDoc } from "components/DocumentEditors/useDocumentApi/types/feed";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import ActiveTasks from "./ActiveTasks";

const feed: FeedDoc = fullTestData.documents.Feed[0];

const stories = storiesOf("Document Editors/Feed", module);

addThemedStories(stories, "Active Tasks", () => <ActiveTasks feedUuid={feed.uuid} />);
