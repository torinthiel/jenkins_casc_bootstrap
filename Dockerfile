# As the source files are split, we need a second image to combine them into a single script
FROM alpine:3.12.0 AS builder

# Prepare work directory
RUN mkdir -p /tmp/configuration_as_code/src
WORKDIR /tmp/configuration_as_code

# Copy sources
COPY ConfigurationAsCodeBootstrap.footer.groovy .
COPY src/main/groovy/pl/torinthiel/jenkins/bootstrap/*.groovy src/

# Combine into final script
RUN sed -e '/^package /d' src/* ConfigurationAsCodeBootstrap.footer.groovy > ConfigurationAsCodeBootstrap.groovy


FROM jenkins/jenkins:2.222.1

# Disable installer, as configuration will be handled in a different way.
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"

# Install needed/wanted plugins
COPY plugins.txt /opt
RUN /usr/local/bin/install-plugins.sh < /opt/plugins.txt

# A stripped-down version of apprived script list, ment to pre-approve the
# script used by the configuration job.
COPY scriptApproval.xml ${REF}

# REF is a standard Jenkins location serving as a reference for $JENKINS_HOME
# Everything from there will be copied to $JENKINS_HOME, without overwriting
# unless the file name ends in .override
# REF_INIT is the reference location for init hooks
ARG REF_INIT=${REF}/init.groovy.d/

# Jenkins startup will copy it from here to $JENKINS_HOME, dropping the
# .override part. As this is a part of image, not something user-configurable,
# we want it to be taken from image on every restart, as this makes upgrade
# work.
COPY --from=builder /tmp/configuration_as_code/ConfigurationAsCodeBootstrap.groovy \
	${REF_INIT}/ConfigurationAsCodeBootstrap.groovy.override
