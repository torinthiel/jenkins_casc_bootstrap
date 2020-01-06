#!/bin/bash
chmod -R a+rX,u+w,og-w src/ ConfigurationAsCodeBootstrap.footer.groovy plugins.txt scriptApproval.xml
docker build -t torinthiel/jenkins-bootstrap:latest .
