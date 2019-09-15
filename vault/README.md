This directory handles preparation and usage of a HashiCorp Vault instance used for revelopment/testing.

*Note that many aspects of this configuration make it not suitable for production.*

Included scripts
* config.sh - configuration, not executable
* start.sh - Starts an instance of Vault, with default configuration. Does not attempt anything except starting instance - no configuration, no unseal
* clean.sh - Stops and removes the instance, and removes the Docker volumes used by it
* command.sh - executes a Vault CLI command on the started instance
* init.sh - starts, initializes, unseals and configures the instance, storing the unseal key
* unseal.sh - unseals the instance, provided the unseal key is present
