#!/bin/bash
docker run -ti --rm --name jenkins_casc -p 28080:8080 -v jenkins_casc:/var/jenkins_home torinthiel/jenkins-bootstrap
