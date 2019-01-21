import * as uuidv4 from "uuid/v4";
import * as loremIpsum from "lorem-ipsum";

import { User } from "src/types";

export const adminUser = {
  uuid: uuidv4(),
  name: "admin",
  group: false
};

export const generateTestUser = (): User => ({
  uuid: uuidv4(),
  name: loremIpsum({ count: 3, units: "words" }),
  group: false
});

export const generateTestGroup = (): User => ({
  uuid: uuidv4(),
  name: loremIpsum({ count: 3, units: "words" }),
  group: true
});
