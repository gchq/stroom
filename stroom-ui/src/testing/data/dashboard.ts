import v4 from "uuid/v4";
import { loremIpsum } from "lorem-ipsum";
import {
  DashboardDoc,
  TabVisibility,
} from "components/DocumentEditors/useDocumentApi/types/dashboard";

export const generate = (): DashboardDoc => ({
  type: "Dashboard",
  uuid: v4(),
  name: loremIpsum({ count: 2, units: "words" }),
  dashboardConfig: {
    components: [],
    layout: {
      preferredSize: {
        size: [1],
      },
      selected: 0,
      tabs: [],
      dimension: 1,
      children: [],
    },
    parameters: "",
    tabVisibility: TabVisibility.SHOW_ALL,
  },
});
