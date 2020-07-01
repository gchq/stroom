import moment from "moment";
import useConfig from "startup/config/useConfig";

interface DateUtil {
  toDateString: (value: number) => string;
}

const useDateUtil = (): DateUtil => {
  const {
    uiPreferences: { dateFormat },
  } = useConfig();

  const toDateString = (value: number) => {
    const onMoment = moment(value);
    return onMoment.format(dateFormat);
  };

  return {
    toDateString,
  };
};

export default useDateUtil;
