/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as React from "react";
import * as moment from "moment";

export const LoginStatsCopy = ({
                                 lastLogin,
                                 loginCount,
                                 dateFormat,
                               }: {
  lastLogin: string;
  loginCount: number;
  dateFormat: string;
}) => {
  if (lastLogin !== undefined) {
    const lastLoginMoment = moment(lastLogin);
    const loginStatsCopy = (
      <div>
        <div className="copy">
          Last login: {lastLoginMoment.fromNow()}, at{" "}
          {lastLoginMoment.format(dateFormat)}{" "}
        </div>
        <div className="copy">Total logins: {loginCount}</div>
      </div>
    );
    return loginStatsCopy;
  } else {
    return <div className="copy">This user has never logged in.</div>;
  }
};

export const AuditCopy = ({
                            createdBy,
                            createdOn,
                            updatedBy,
                            updatedOn,
                            dateFormat,
                          }: {
  createdBy: string;
  createdOn: string;
  updatedBy: string;
  updatedOn: string;
  dateFormat: string;
}) => {
  return (
    <div>
      <OnCopy on={createdOn} verb="Created" dateFormat={dateFormat}/>
      <ByCopy by={createdBy} verb="Created by"/>
      <OnCopy
        on={updatedOn}
        verb="Updated"
        dateFormat={dateFormat}
        fallbackCopy="This has never been updated."
      />
      <ByCopy by={updatedBy} verb="Updated by"/>
    </div>
  );
};

export const OnCopy = ({
                         on,
                         verb,
                         dateFormat,
                         fallbackCopy,
                       }: {
  on: string;
  verb: string;
  dateFormat: string;
  fallbackCopy?: string;
}) => {
  if (on !== undefined && on !== null) {
    const onMoment = moment(on);
    return (
      <div className="copy">
        <strong>{verb}</strong> at{" "}
        {onMoment.format(dateFormat)}.{" "}
      </div>
    );
  } else {
    return <div className="copy">{fallbackCopy}</div>;
  }
};

export const OnCopyMs = ({
                           on,
                           verb,
                           dateFormat,
                           fallbackCopy,
                         }: {
  on: number;
  verb: string;
  dateFormat: string;
  fallbackCopy?: string;
}) => {
  if (on !== undefined && on !== null) {
    const onMoment = moment(on);
    return (
      <div className="copy">
        <strong>{verb}</strong> at{" "}
        {onMoment.format(dateFormat)}.{" "}
      </div>
    );
  } else {
    return <div className="copy">{fallbackCopy}</div>;
  }
};

export const ByCopy = ({
                         by,
                         verb,
                         fallbackCopy,
                       }: {
  by: string;
  verb: string;
  fallbackCopy?: string;
}) => {
  if (by !== undefined && by !== null) {
    return (
      <div className="copy">
        <strong>{verb}</strong> &apos;{by}&apos;.
      </div>
    );
  } else {
    return <div className="copy">{fallbackCopy}</div>;
  }
};
