import * as uuidv4 from "uuid/v4";
import * as loremIpsum from "lorem-ipsum";

import { User } from "src/types";

export const adminUser: User = {
  uuid: uuidv4(),
  name: "admin",
  isGroup: false
};

export const generateTestUser = (): User => ({
  uuid: uuidv4(),
  name: loremIpsum({ count: 3, units: "words" }),
  isGroup: false
});

export const generateTestGroup = (): User => ({
  uuid: uuidv4(),
  name: loremIpsum({ count: 3, units: "words" }),
  isGroup: true
});
