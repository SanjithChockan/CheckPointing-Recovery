#!/bin/bash

# Command to grant permission to file to run [RUN THIS]: chmod +x cleanup.sh

# Change this to your netid [CHANGE THIS]
netid=sxc180101

# Root directory of project [CHANGE THIS]
PROJDIR=/home/010/s/sx/sxc180101/AdvancedOS/project3

# Directory where the config file is located on your local system [CHANGE THIS]
CONFIGLOCAL=config.txt

# extension for hosts [CHANGE THIS if using a different host system (setting to "" should suffice)]
hostExtension="utdallas.edu"

# loop through hosts, remove comment lines starting with # and $ and any carriage returns
n=0
# remove comments | remove other comments | remove carriage returns
cat $CONFIGLOCAL | sed -e "s/#.*//" | sed -e "/^\s*$/d" | sed -e "s/\r$//" |
(
    # read the first valid line and collect only the number of hosts
    read i
    echo $i
    ii=$( echo $i| awk '{ print $1 }' )
    echo Hosts: $ii

    # for each host, loop
    while [[ $n -lt $ii ]]
    do
        # collect the name of the host from file; collect the second element on the line as a host
        echo next host:
        read line
        host=$( echo $line | awk '{ print $2 }' )

        # add host extension to string if missing from domain name
            if [[ "$host" != *"$hostExtension"* ]];
        then
            host="$host.$hostExtension"
        fi
        echo $host
     
        # issue command
        #echo issued command: -e "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $netid@$host killall -u $netid" &
        osascript -e 'tell app "Terminal"
        do script "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no '$netid@$host' killall -u '$netid'"
        end tell'
        # sleep 1

        # increment loop counter
        n=$(( n + 1 ))
    done
)

echo "Cleanup complete"
