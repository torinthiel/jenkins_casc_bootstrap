# A container designed to host a simple, hardcoded git repostory
FROM alpine:3.10.3

# Install necessary software
RUN apk add git openssh
RUN /usr/bin/ssh-keygen -A

# Setup user
RUN adduser --disabled-password git && \
	passwd -d -u git
WORKDIR /home/git
COPY --chown=git:git test_key_rsa.pub .ssh/authorized_keys

# Setup permissions and git for that user
USER git
RUN chmod 600 /home/git/.ssh/authorized_keys && \
	git config --global user.email "test@xample.com" && \
	git config --global user.name "Test user" && \
	mkdir repo

# Setup the repository
COPY --chown=git:git repo-prepare.sh .
COPY --chown=git:git repository-contents repository-contents/
RUN cd repo && /bin/sh -ex ../repo-prepare.sh

# Setup container startup
USER root
CMD ["/usr/sbin/sshd", "-De"]]
