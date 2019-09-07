FROM jenkins/jenkins:2.176.3

# Install needed/wanted plugins
COPY plugins.txt /opt
RUN /usr/local/bin/install-plugins.sh < /opt/plugins.txt

# REF is a standard Jenkins location serving as a reference for $JENKINS_HOME
# Everything from there will be copied to $JENKINS_HOME, without overwriting
# unless the file name ends in .override
# REF_INIT is the reference location for init hooks
# As of version 2.176.2 this needs to be below install-plugins, as it breaks
# it. Most probably the next version will work with this above, or maybe even
# not there at all.
ARG REF=/usr/share/jenkins/ref/
ARG REF_INIT=${REF}/init.groovy.d/

# Jenkins startup will copy it from here to $JENKINS_HOME, dropping the
# .override part. As this is a part of image, not something user-configurable,
# we want it to be taken from image on every restart, as this makes upgrade
# work.
COPY ConfigurationAsCodeBootstrap.groovy ${REF_INIT}/ConfigurationAsCodeBootstrap.groovy.override
