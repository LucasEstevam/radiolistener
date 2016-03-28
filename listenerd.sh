#!/bin/sh

start () {
    echo -n "Starting listener..."
    currentdir=`pwd`
    listenertag=`ec2-describe-tags --filter "resource-id=$(ec2metadata --instance-id)" --filter "key=Name" | cut -f5`
    # Start daemon
    daemon --chdir=$currentdir --command "java -server -Dlistener.tag=$listenertag -Xmx2000M -cp ".:lib/*:cfg/:./*" br.com.radiolistener.Listener.Listener" --output=$currentdir/output.log --name=listener

    RETVAL=$?
    if [ $RETVAL = 0 ]
    then
        echo "done."
    else
        echo "failed. See error code for more information."
    fi
    return $RETVAL
}

stop () {
    # Stop daemon
    echo -n "Stopping Listener..."

    daemon --stop --name=listener  --verbose
    RETVAL=$?

    if [ $RETVAL = 0 ]
    then
        echo "Done."
    else
        echo "Failed. See error code for more information."
    fi
    return $RETVAL
}


restart () {
    daemon --restart --name=listener  --verbose
}


status () {
    # Report on the status of the daemon
    daemon --running --verbose --name=listener
    return $?
}


case "$1" in
    start)
        start
    ;;
    status)
        status
    ;;
    stop)
        stop
    ;;
    restart)
        restart
    ;;
    *)
        echo $"Usage: listenerd {start|status|stop|restart}"
        exit 3
    ;;
esac

exit $RETVAL
