#!/bin/bash 

cd "$(dirname $(realpath $0))"

KERVER=$(uname -r | grep -o "^4\.")

if [ "$KERVER" != "4." ]; then
  echo "Warning! The linux kernel version must great or equal than 4."
fi

HW=$(uname -m)

if [ "$HW" != "x86_64" ]; then
  echo "Warning! Aion blockchain platform must be running on the 64 bits architecture"
fi

DIST=$(lsb_release -i | grep -o "Ubuntu")

if [ "$DIST" != "Ubuntu" ]; then
  echo "Warning! Aion blockchain is fully compatible with the Ubuntu distribution. Your current system is not Ubuntu distribution. It may has some issues."
fi

MAJVER=$(lsb_release -r | grep -o "[0-9][0-9]" | sed -n 1p)
if [ "$MAJVER" -lt "16" ]; then
  echo "Warning! Aion blockchain is fully compatible with the Ubuntu version 16.04. Your current system is older than Ubuntu 16.04. It may has some issues."
fi

ARG=$@

#if [ "$ARG" == "--close" ]; then
#    PID=$(<./tmp/aion.pid)
#    kill -2 $PID
#    rm -r ./tmp
#    exit 0
#fi

# add execute permission to rt
chmod +x ./rt/bin/*

JAVA_CMD=java
if [ -d "$JAVA_HOME" ]; then
        JAVA_CMD="$JAVA_HOME/bin/java"
fi

trap "exit" INT TERM
trap "exit_kernel" EXIT

#env EVMJIT="-cache=1" java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -Xms4g  \

echo testing1... > /tmp/junk

exit_kernel() { 
    echo "TRAP: kernel_pid = $kernel_pid" >> /tmp/junk
    if [ ! -z "$kernel_pid" ]; then
        echo "killing..." >> /tmp/junk
        kill "$kernel_pid" 
    fi
    exit 0
}

env EVMJIT="-cache=1" $JAVA_CMD -Xms4g  \
        -cp "./lib/*:./lib/libminiupnp/*:./mod/*:/home/sergiu/opt/jdk-10.0.1/lib" org.aion.Aion "$@" &
kernel_pid=$!
wait
