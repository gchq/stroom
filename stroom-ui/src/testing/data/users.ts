import { loremIpsum } from "lorem-ipsum";
import { Account } from "components/users/types";

const lr = (count: number) => loremIpsum({ count, units: "words" });
const getUser = (): Account => {
  return {
    id: lr(1),
    email: lr(1),
    enabled: true,
    inactive: false,
    locked: false,
    processingAccount: false,
    firstName: lr(1),
    lastName: lr(1),
    comments: lr(20),
    password: "",
    forcePasswordChange: true,
    neverExpires: false,
    loginCount: 0,
    createdByUser: lr(1),
    createdOn: "2019-01-01T23:01:01.111Z",
  };
};

const newUser = getUser();

const wellUsedUser = getUser();
wellUsedUser.forcePasswordChange = false;
wellUsedUser.loginCount = 99;
wellUsedUser.updatedOn = "2019-02-02T23:01:01.111Z";
wellUsedUser.updatedByUser = lr(1);
wellUsedUser.lastLogin = "2019-04-03T23:01:01.222Z";

const disabledUser = getUser();
disabledUser.enabled = false;

const inactiveUser = getUser();
inactiveUser.inactive = true;

const lockedUser = getUser();
lockedUser.locked = true;

export { newUser, wellUsedUser, disabledUser, inactiveUser, lockedUser };
