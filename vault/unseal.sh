#!/bin/bash

. config.sh

docker run \
	--rm \
	--env-file .unseal.env \
	--link $VAULT_CONTAINER_NAME:vault \
	vault:$VAULT_TAG sh -c \
	'VAULT_ADDR=http://$VAULT_PORT_8200_TCP_ADDR:$VAULT_PORT_8200_TCP_PORT vault operator unseal $UNSEAL_KEY'
