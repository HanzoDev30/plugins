#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

apt update
apt install -y ruby ruby-dev build-essential

ruby --version
gem --version

gem install solargraph
gem install rufo