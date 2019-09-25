#!/bin/bash
set -e

echo '===================='
echo '- Install packages -'
echo '===================='

# Unset Connect URL to prevent nuxeoctl from reaching Connect:
# - We rely on local packages.
# - The network might not be available.
# - The Connect server might not be responding.
connectUrlProp=org.nuxeo.connect.url=
echo $connectUrlProp >> $NUXEO_CONF \

# List and install packages
packagesDir=/packages
packages=$(find $packagesDir -name *.zip)
if [ ! -z "$packages" ]; then
  echo 'Packages to install:'
  cat << EOF
$packages
EOF
  echo
  $NUXEO_HOME/bin/nuxeoctl mp-install --accept yes --nodeps $packages
else
  echo 'Found no packages to install.'
fi
echo

# Reset Connect URL
sed -i "/$connectUrlProp/d" $NUXEO_CONF

echo "Clean up $packagesDir"
rm -rf $packagesDir
echo

backupDir=$NUXEO_HOME/packages/backup
echo "Clean up $backupDir"
rm -rf $backupDir
echo

tmpDir=$NUXEO_HOME/packages/tmp
echo "Clean up $tmpDir"
rm -rf $tmpDir
echo
