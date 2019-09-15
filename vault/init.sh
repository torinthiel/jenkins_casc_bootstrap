#!/bin/bash

. config.sh

# Start the (uninitialized) instance
./start.sh

# Initialize & retrieve unseal key and root token
INIT_OUTPUT=`./command.sh operator init --key-shares=1 --key-threshold=1`
VAULT_TOKEN=`echo "$INIT_OUTPUT" | grep '^Initial Root Token' | cut -d: -f2`
UNSEAL_KEY=`echo "$INIT_OUTPUT" | grep '^Unseal Key' | cut -d: -f2`
# Store the unseal key for future uses
echo "UNSEAL_KEY=$UNSEAL_KEY" > .unseal.env

# Unseal the instance
./unseal.sh

# Configure policy
./command.sh policy write jenkins_policy - <<EOF
path "secret/+/jenkins*" {
	capabilities = ["create", "read", "update", "delete", "list"]
}
EOF

# Configure user/password authentication
./command.sh auth enable userpass
./command.sh write auth/userpass/users/jenkins password=S3cRet policies=jenkins_policy

# Configure secret backend
./command.sh secrets enable -path=secret kv-v2

# Revoke the root token after the initial configuration
./command.sh token revoke $VAULT_TOKEN
