#!/bin/sh
# Component scripts are sourced; $0 is the CLI wrapper, not this directory.
SUITE="jammy"
UBUNTU_FLAVOR="desktop"

# do_install is inherited from bootstrap/debian via deploy.conf.
do_configure()
{
    apt_install "ubuntu-${UBUNTU_FLAVOR}"
}
