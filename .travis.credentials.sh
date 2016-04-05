#!/bin/bash
mkdir ~/.bintray/
BINTRAY_FILE=$HOME/.bintray/.credentials
cat <<EOF >$BINTRAY_FILE
realm = Bintray API Realm
host = api.bintray.com
user = $BINTRAY_USER
password = $BINTRAY_API_KEY
EOF
