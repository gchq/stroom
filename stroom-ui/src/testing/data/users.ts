import { loremIpsum } from "lorem-ipsum";
import { Account } from "components/users/types";
import { now } from "moment";

const lr = (count: number) => loremIpsum({ count, units: "words" });
const getUser = (): Account => {
  return {
    id: Math.floor(Math.random() * 1000),
    email: lr(1),
    enabled: true,
    inactive: false,
    locked: false,
    processingAccount: false,
    firstName: lr(1),
    lastName: lr(1),
    comments: lr(20),
    forcePasswordChange: true,
    neverExpires: false,
    loginCount: 0,
    createUser: lr(1),
    createTimeMs: now(),
  };
};

const newUser = getUser();

const wellUsedUser = getUser();
wellUsedUser.forcePasswordChange = false;
wellUsedUser.loginCount = 99;
wellUsedUser.updateTimeMs = now();
wellUsedUser.updateUser = lr(1);
wellUsedUser.lastLoginMs = now();

const disabledUser = getUser();
disabledUser.enabled = false;

const inactiveUser = getUser();
inactiveUser.inactive = true;

const lockedUser = getUser();
lockedUser.locked = true;

export { newUser, wellUsedUser, disabledUser, inactiveUser, lockedUser };
