#!/bin/sh -e

# Creates a new branch, based on master,  with contents added from specified directory.
# $1 - branch name
# $2 - directory with files for the commit
# $3 - commit message
create_branch_from_dir() {
	git checkout -b $1 master
	cp -r $2/* .
	git add .
	git commit -m"$3"
}

# Adds a new commit to specified branch, with contents added from specified directory
# $1 - branch name
# $2 - directory with files for the commit
# $3 - commit message
add_files_from_dir() {
	git checkout $1
	cp -r $2/* .
	git add .
	git commit -m"$3"
}


git init

cat > README <<EOF
Repository for integration testing of Jenkins Configuration-as-Code Bootstrap.
Since the repository is programatically generated on each test, history
will be rewriten. Do not expect any kind of consistency from the contents.
EOF
git add .
git commit -m'Initial commit, readme'

add_files_from_dir master ../repository-contents/master "Basic smoke test"
create_branch_from_dir other_branch ../repository-contents/other_branch "Different branch test"

git checkout master
