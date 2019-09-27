FROM jenkins/jenkins:2.190.1

# Install needed/wanted plugins
COPY plugins.txt /opt
RUN /usr/local/bin/install-plugins.sh < /opt/plugins.txt

# REF is a standard Jenkins location serving as a reference for $JENKINS_HOME
# Everything from there will be copied to $JENKINS_HOME, without overwriting
# unless the file name ends in .override
# REF_INIT is the reference location for init hooks
ARG REF_INIT=${REF}/init.groovy.d/

# Jenkins startup will copy it from here to $JENKINS_HOME, dropping the
# .override part. As this is a part of image, not something user-configurable,
# we want it to be taken from image on every restart, as this makes upgrade
# work.
COPY ConfigurationAsCodeBootstrap.groovy ${REF_INIT}/ConfigurationAsCodeBootstrap.groovy.override
