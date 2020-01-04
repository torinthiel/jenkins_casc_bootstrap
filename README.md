Jenkins Configuration-as-Code Bootstrap
=======================================

*Warning: This is currently a Work-in-progress, do not expect any kind of
usability nor stability*

This repository serves as a base for setting up a dockerized Jenkins instance
with all configuration automated and managed in SCM.

It builds upon a great framework founded by:
* [Jenkins](https://jenkins.io/)
* [Jenkins Configuration-as-Code](https://github.com/jenkinsci/configuration-as-code-plugin) plugin
* [Jenkins JobDSL](https://github.com/jenkinsci/job-dsl-plugin/wiki) plugin
* [HashiCorp Vault](https://vaultproject.io/)

Features (When finished ;) )
--------

Jenkins configuration taken from Git repository via Configuration-as-Code plugin.

Secrets downloaded from HashiCorp Vault.

Multiple configuration pieces possible, with user-defined priority, so it's
possible to have several Jenkins instances that share a lot of configuration.

Possible uses:

* Cloning Jenkins instance with overriding e.g. notifications and agents,
  for testing upgrades
* Several instances sharing some parts of common configuration, while having
  different tweaks in every configuration.


Flow
----

On startup, the bootstrap script connects to Vault. The Vault's URL,
credentials and path within the Vault are provided via environment
variables/files, e.g. using [Docker
secrets](https://docs.docker.com/engine/swarm/secrets/). From there the scripts
get the SSH keys that are preconfigured into Jenkins as a Credential, and a
location of git repository containing configuration.

Later the scripts sets up and schedules a job that will clone the selected
repository, assemble configuration into the `$JENKINS_HOME` folder and apply it
to the running instance.

This process ensures that
a) There are no configuration parts present in the image, everything comes in
   (directly or indirectly) via environment variables.
b) The configuration process is repeatable - user can re-run the configuration
   job to revert to stored configuration, or periodically
c) The configuration is modular, that is an organization can have global
   configuration for all it's instances, with some more specific overrides.

Configuration
-------------

The following configuration variables are supported:

* CASCB_VAULT_URL - The URL to Vault server
* CASCB_VAULT_USER - The username used to login
* CASCB_VAULT_PW - The password used to login
* CASCB_VAULT_PATHS - Comma-separated list of paths from which plugin should retrieve configuration
* CASCB_VAULT_FILE - Path to properties file that will be scanned for above variables

Each of the variables is supported with either `CASCB_` prefix as indicated in
the above list or with `CASC_` prefix used also by the Configuration-as-Code
plugin. If the `CASCB_VAULT_FILE` or `CASC_VAULT_FILE` variable is present the
file it points at will be scanned for the same variables. The file indicated by
`CASCB_VAULT_FILE` is scanned only for variables with `CASCB_` prefix.

In case a variable is present in several places the following precedence
applies, from least important:
* _CASC_-prefixed variable in `CASC_VAULT_FILE`
* _CASCB_-prefixed variable in `CASC_VAULT_FILE`
* _CASC_-prefixed variable in environment
* _CASCB_-prefixed variable in `CASCB_VAULT_FILE`
* _CASCB_-prefixed variable in environment

The following values are retrieved from Vault:

* cascb_ssh_key - the SSH key used to connect to repository with configuration. Required.
* cascb_ssh_user - the username to use for git authentication. Required.
* cascb_ssh_description - the description to use for the created SSH credential. Defaults to empty if not provided.
* cascb_ssh_id - the ID the generated credential will use. Defaults to 'ssh-key' if not provided.
* cascb_repo_url - the URL of git repository containing configuration. Required.
* cascb_repo_branch - the branch that should be checked out and contain configuration. Defaults to 'master' if not provided.
* cascb_repo_directories - the comma separated  list of directories within that branch that contain configuration. All
  `.yaml` files directly within any of those directories (not in subdirectory) will have their contents applied. If a file
  with same name exists in more than one directory, the last one takes precedence. Missing directories are ignored.
  If not provided, defaults to '.', the root directory of repository.
* cascb_job_name - the name of generated job. Full path, where each /-separated component will be converted to a folder.
  Defaults to 'config' if not provided.
* cascb_job_description - the description that will be added to generated job. Defaults to empty if not provided.
* cascb_job_poll_schedule - sets up Jenkins to periodically poll git and re-apply configuration if changes are found.
  Needs to be a proper Jenkins cron expression. Defaults to empty, that is do not set polling. 

If any of those values exists in more than one path from CASCB_VAULT_PATHS, than the last value takes precedence. As an
exception, if the directory list begins with (+), the rest of list is appended to the previous value of the field (if any).

Building
--------

Simply use the provided `./build.sh` script or use plain `docker build .` command to create the image.
You can customize `plugins.txt` beforehand to provide the plugins you need.

Run maven build to run tests for the functionality, the resulting maven
artifact is not used anywhere and needs not be published.
