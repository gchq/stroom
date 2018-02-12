#!/usr/bin/env bash
# Simple script to start and stop Stroom.

# Exit script on any error
set -e

#Shell Colour constants for use in 'echo -e'
#e.g.  echo -e "My message ${GREEN}with just this text in green${NC}"
RED='\033[1;31m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
BLUE='\033[1;34m'
NC='\033[0m' # No Colour


NO_ARGUMENT_MESSAGE="Please supply an argument: either ${YELLOW}start${NC}, ${YELLOW}stop${NC}, ${YELLOW}log${NC} or ${YELLOW}errors${NC}."
# Check script's params
if [ $# -ne 1 ]; then
    echo -e $NO_ARGUMENT_MESSAGE
    echo -e ""
    exit 1
fi

# Set up variables for this script
mkdir -p log
SERVICE_NAME=Stroom
PATH_TO_JAR=stroom-app-all.jar
PID_PATH_NAME=log/stroom.pid
LOG_FILE=log/stroom.log
ERROR_LOG_FILE=log/stroom_error.log

startStroom(){
    echo -e "Starting ${GREEN}Stroom${NC}"
    echo -e "${BLUE}---------------${NC}"
    echo -e ""
    STROOM_HOME="$HOME/.stroom/"

    # Create the ~/.stroom directory if it doesn't exist
    mkdir -p $STROOM_HOME
    echo -e " - ${GREEN}Stroom's${NC} working directory is ${YELLOW}~/.stroom${NC}"

    # Always copy the config over - the config here is the master config
    echo -e " - Updating the config (${YELLOW}~/.stroom/stroom.conf${NC})"
    cp -rf stroom.conf $STROOM_HOME

    # Run Stroom
    echo -e " - Running ${GREEN}Stroom${NC}"
#    java -jar stroom-app-all.jar server config.yml
    nohup java -jar $PATH_TO_JAR server config.yml > $LOG_FILE 2> $ERROR_LOG_FILE &
    echo $! > $PID_PATH_NAME
    echo -e ""
    echo -e "Run '${YELLOW}./stroom.sh log${NC}' to see what's happening, or '${YELLOW}./stroom.sh errors${NC}' for errors."
    echo -e ""
}

stopStroom(){
    PID=$(cat $PID_PATH_NAME);
    kill $PID;
    echo -e "Stopping ${GREEN}Stroom${NC} gracefully. Run '${YELLOW}./stroom.sh log${NC}' to check for progress."
    echo -e ""
    rm $PID_PATH_NAME
}

case $1 in
    start)
        if [ ! -f $PID_PATH_NAME ]; then
            startStroom
        else
            echo -e "${GREEN}Stroom${NC} is already running..."
            echo -e ""
        fi
    ;;
    stop)
        if [ -f $PID_PATH_NAME ]; then
            stopStroom
        else
            echo -e "${RED}Cannot${NC} stop ${GREEN}Stroom${NC} because it does not appear to be running (there is no PID file)."
        fi
    ;;
    log)
        echo -e "Tailing ${GREEN}Stroom's${NC} log file at ${YELLOW}$LOG_FILE${NC}. Use ${YELLOW}Ctrl+C${NC} to stop tailing."
        echo -e ""
        tail -f $LOG_FILE
    ;;
    errors)
        echo -e "Tailing ${GREEN}Stroom's${NC} error log at ${YELLOW}$ERROR_LOG_FILE${NC}. Use ${YELLOW}Ctrl+C${NC} to stop tailing."
        echo -e ""
        tail -f $ERROR_LOG_FILE
    ;;
    help)
        echo -e $NO_ARGUMENT_MESSAGE
        echo -e ""
    ;;
esac