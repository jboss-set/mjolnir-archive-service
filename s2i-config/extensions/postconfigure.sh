#!/usr/bin/env bash

ENV_DIR=/etc/eap-environment

# Run EAP modifications CLI script
#${JBOSS_HOME}/bin/jboss-cli.sh --file=${ENV_DIR}/eap-config.cli


if [ -f ${ENV_DIR}/configure.sh ]; then
  echo "Evaluating ${ENV_DIR}/configure.sh"
  source ${ENV_DIR}/configure.sh
else
  echo "${ENV_DIR}/configure.sh script not found"
fi
