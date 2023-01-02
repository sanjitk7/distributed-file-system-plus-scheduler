

if [[ $1 == "introducer" ]]
then
    git pull origin main
    clear
    cd introducer/
    echo "Starting introducer..."
    echo 'Executing command java -cp "target/introducer-1.0-SNAPSHOT.jar:target/dependency/*" com.mp3.introducer.Introducer'
    java -cp "target/introducer-1.0-SNAPSHOT.jar:target/dependency/*" com.mp3.introducer.Introducer

else
    git pull origin main
    clear
    rm -rf sdfs/output/*
    cd sdfs/
    echo "Starting client process..."
    echo 'Executing command java -cp "target/sdfs-1.0-SNAPSHOT.jar:target/dependency/*" com.mp3.sdfs.Node'
    java -cp "target/sdfs-1.0-SNAPSHOT.jar:target/dependency/*" com.mp3.sdfs.Node
fi