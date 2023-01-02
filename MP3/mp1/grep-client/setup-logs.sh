#!/bin/bash

# SOME GLOBAL VARIABLES
path_to_log_files="/Users/aruna/sanjit/distributed-grep-system/log-file-generator/output/logs"
netid="aruna2"
vm_count=4

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

echo "Setting up..."

pushd ../log-file-generator
rm -rf output/logs
java -cp "target/log-file-generator-1.0-SNAPSHOT.jar:target/dependency/*" com.cs425.mp1.logfilegenerator.App
popd

for i in $(seq 1 $vm_count); do
    vm=${list_of_vms[(($i-1))]}
    scp "${path_to_log_files}/vm${i}.log" "${vm}:/home/${netid}/"
done

echo "Finished copying files!"
