#!/bin/bash

# SOME GLOBAL VARIABLES
path_to_server_jar="/Users/aruna/cs425-mp1/grep-server/target/"
netid="aruna2"

list_of_vms=("${netid}@fa22-cs425-6501.cs.illinois.edu"
             "${netid}@fa22-cs425-6502.cs.illinois.edu"
             "${netid}@fa22-cs425-6503.cs.illinois.edu"
             "${netid}@fa22-cs425-6504.cs.illinois.edu"
             "${netid}@fa22-cs425-6505.cs.illinois.edu"
             "${netid}@fa22-cs425-6506.cs.illinois.edu"
             "${netid}@fa22-cs425-6507.cs.illinois.edu"
             "${netid}@fa22-cs425-6508.cs.illinois.edu"
             "${netid}@fa22-cs425-6509.cs.illinois.edu"
             "${netid}@fa22-cs425-6510.cs.illinois.edu"
)

if [ -z "$1" ]; then
    echo "Usage: setup.sh <vm id>"
    echo "ERROR: script needs vm id value ranging from 1-10"
    exit 1
fi

i=$1

vm=${list_of_vms[(($i-1))]}

# Copy the grep-server jar files
echo "Setting up..."
scp -r ${path_to_server_jar} "${vm}:/home/${netid}/"

echo "Finished copying files!"
echo "Starting server on ${vm} on port 6002... Please open a new terminal to run this script again."

# Start running the server
ssh ${vm} 'java -cp "target/grepservermp-1.0-SNAPSHOT.jar:target/dependency/*" com.grepservermp.app.App 6002'
