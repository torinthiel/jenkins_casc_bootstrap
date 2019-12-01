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


Flow
----

On startup, the bootstrap script connects to Vault. The Vault's URL,
credentials and path within the vault are provided via environment
variables/files, e.g. using [Docker
secrets](https://docs.docker.com/engine/swarm/secrets/). From there the scripts
get the SSH keys that are preconfigured into Jenkins as a Credential, and a
location of git repository containing configuration.

Later the scripts sets up and schedules a job that will clone the selected
repository, assemble configuration into the `$JENKINS_HOME` and apply it to the
running instance.

This process ensures that
a) There are no configuration parts present in the image, everything comes in
   (directly or indirectly) via environment variables.
b) The configuration process is repeatable - user can re-run the configuration
   job to revert to stored configuration, or periodically
c) The configuration is modular, that is an organization can have global
   configuration for all it's instances, with some more specific overrides.

Building
--------

Simply use the provided `./build.sh` script or use plain `docker build .` command to create the image.
You can customize `plugins.txt` beforehand to provide the plugins you need.

Run maven build to run tests for the functionality, the resulting maven
artifact is not used anywhere and needs not be published.
