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

mkdir -p log

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

warn() {
    echo -e "${RED}Warning:${NC} $1"
}

info() {
    echo -e "${GREEN}Info:${NC} $1"
}
