#!bin/bash

export ARCHDIR=$(pwd)/mips-x86.linux-xgcc
export PATH=$ARCHDIR:$PATH

mips-gcc --version