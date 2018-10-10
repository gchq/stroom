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

NO_ARGUMENT_MESSAGE="Please supply an argument: either ${YELLOW}start${NC} or ${YELLOW}stop${NC}."
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
JAVA_OPTS="-Xms512m -Xmx2048m"

startStroom(){
    echo -e "Starting ${GREEN}Stroom${NC}"
    echo -e "${BLUE}---------------${NC}"
    echo -e ""

    # Run Stroom
    echo -e " - Running ${GREEN}Stroom${NC}"
#    java -jar stroom-app-all.jar server config.yml
    nohup java $JAVA_OPTS -jar $PATH_TO_JAR server config.yml &
    echo $! > $PID_PATH_NAME
    echo -e ""
}

stopStroom(){
    PID=$(cat $PID_PATH_NAME);
    kill $PID;
    echo -e "Stopping ${GREEN}Stroom${NC} gracefully."
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
    help)
        echo -e $NO_ARGUMENT_MESSAGE
        echo -e ""
    ;;
esac