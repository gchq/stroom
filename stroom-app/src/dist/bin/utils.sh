#!/usr/bin/env bash
#
# Common to all Stroom management scripts

# Exit script on any error
set -e

#Shell Colour constants for use in 'echo -e'
#e.g.  echo -e "My message ${GREEN}with just this text in green${NC}"
RED='\033[1;31m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
BLUE='\033[1;34m'
BLUE2='\033[1;34m'
NC='\033[0m' # No Colour

mkdir -p logs

greeting() {
  echo -e "${GREEN}                    ╔╬═${NC}"
  echo -e "${GREEN}                    ╬╣═${NC}"
  echo -e "${GREEN}                    ╬╣╕${NC}"
  echo -e "${GREEN}   ┌╦╬╣╣╣╣╣╣╣╣╩╬╗╬╣╣╣╣╣╣╣╣╣╬╬╗╬╣╣╣╩╗╬╬╣╣╣╬╗┐  ╔╬╬╣╣╣╬╗┐    ╔╬╣╣╣╣╦╖ ╔╬╣╣╣╬╦╖${NC}"
  echo -e "${GREEN}   ╣╣╛              ╬╣╛    ╬╣╩╙  ╔╣╣╝    ╙╩╣╬╣╣╝    ╙╩╣╬ ┌╬╣╝    ╙╣╣╣╝    ╩╣╬${NC}"
  echo -e "${GREEN}   ╬╣╬╗╗╗╗╗╗╗╖      ╬╣═   ╬╣╬   ╔╣╣        ╠╣╣        ╠╣╬╠╣╡      ╟╣╡      ╬╣╕${NC}"
  echo -e "${GREEN}     ╙╙╙╙╙╙╙╙╬╣╗    ╬╣═   ╬╣╡   ╙╣╣        ╠╣╣        ╠╣╬╠╣╡      ╠╣╡      ╬╣╛${NC}"
  echo -e "${GREEN}             ╓╣╣    ╬╣═   ╬╣╡    ╙╣╬╗    ╓╬╣╬╣╬╗    ╓╬╣╬ ╠╣╡      ╠╣╡      ╬╣╛${NC}"
  echo -e "${GREEN}   ┌╦╬╣╣╣╣╣╣╣╣╩     ╬╣═   ╬╣╡      ╙╩╣╣╣╣╬╝   ╙╩╣╣╣╣╬╝   ╠╣╡      ╠╣╡      ╬╣╛${NC}"
  echo -e ''
}

ask_about_logs() {
  read -n1 -r -p $'  - Press \e[94mspace\e[0m or \e[94menter\e[0m to see the logs, \e[94manything\e[0m else to return to the command line.' key

  echo -e ''
  
  if [ "$key" = '' ]; then
      info "Press ${BLUE}ctrl+c${NC} to stop following the logs."
      source ../logs.sh
  else
      info "Run ${BLUE}./logs.sh${NC} to see the log.\n"
  fi
}

error() {
    echo -e "${RED}Error:${NC} $1"
}

warn() {
    echo -e "${YELLOW}Warning:${NC} $1"
}

info() {
    echo -e "${GREEN}Info:${NC} $1"
}

wait_for_200_response() {
    if [[ $# -ne 1 ]]; then
        error "Invalid arguments to wait_for_200_response(), expecting a URL to wait for."
        exit 1
    fi

    local -r url=$1
    local -r maxWaitSecs=120
    echo

    n=0
    # Keep retrying for maxWaitSecs
    until [ $n -ge ${maxWaitSecs} ]
    do
        # Check for a configuration parsing error
        LOG_ERROR_PATTERN="io.dropwizard.configuration.ConfigurationParsingException"
        SCRIPT_LOG_LOCATION="logs/start.sh.log"

        if grep -q "${LOG_ERROR_PATTERN}" "${SCRIPT_LOG_LOCATION}"; then
            echo -e
            error "It looks like you have a problem with something in ${BLUE}config/config.yml${NC}. Look in ${BLUE}logs/start.sh.log${NC} for the details.${NC}"
            break
            exit 1
        fi

        # OR with true to prevent the non-zero exit code from curl from stopping our script
        responseCode=$(curl -sL -w "%{http_code}\\n" "${url}" -o /dev/null || true)
        #echo "Response code: ${responseCode}"
        if [[ "${responseCode}" = "200" ]]; then
            break
        fi
        # print a simple unbounded progress bar, increasing every 2s
        mod=$(($n%2))
        if [[ ${mod} -eq 0 ]]; then
            printf '.'
        fi

        n=$[$n+1]
        # sleep for two secs
        sleep 1
    done
    printf "\n"

    if [[ $n -ge ${maxWaitSecs} ]]; then
        echo -e "${RED}Gave up wating for stroom to start up, check the logs: (${BLUE}./logs${NC}, or ${BLUE}logs/start.sh.log${NC}${RED})${NC}"
    fi
}
