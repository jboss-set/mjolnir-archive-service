#!/usr/bin/env bash

echo "Executing install.sh script"

injected_dir=$1
source /usr/local/s2i/install-common.sh

echo "Installing postgresql driver"
install_modules ${injected_dir}/extensions/extras/modules
configure_drivers ${injected_dir}/extensions/extras/drivers.env

echo "Copy ${injected_dir}/extensions/postconfigure.sh to ${JBOSS_HOME}/extensions/"
mkdir -p "${JBOSS_HOME}/extensions/"
cp "${injected_dir}/extensions/postconfigure.sh" "${JBOSS_HOME}/extensions/"
