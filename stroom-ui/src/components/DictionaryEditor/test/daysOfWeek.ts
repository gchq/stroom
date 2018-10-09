import { Dictionary } from "../../../types";

export default {
  docRef: {
    type: "Dictionary",
    uuid: "daysOfWeek",
    name: "Days of Week"
  },
  description: "All the days of the week",
  data: `monday
  tuesday
  wednesday
  thursday
  friday
  saturday
  sunday
  `,
  imports: []
} as Dictionary;
