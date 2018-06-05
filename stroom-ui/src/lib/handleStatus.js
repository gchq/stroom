import { HttpError } from './ErrorTypes';

export default (response) => {
  if (response.status === 200) {
    return Promise.resolve(response);
  }
  return Promise.reject(new HttpError(response.status, response.statusText));
};
