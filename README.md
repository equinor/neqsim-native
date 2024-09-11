![NeqSim Logo](https://github.com/equinor/neqsim/blob/master/docs/wiki/neqsimlogocircleflatsmall.png)
# NeqSim-native
NeqSim is a library for calculation of fluid behavior, phase equilibrium and process simulation. This project compiles NeqSim into a native shared library using GraalVM.

## Getting started 
Install the [GraalVM JDK](https://www.graalvm.org/) and read the [getting started documentation](https://www.graalvm.org/latest/docs/getting-started/).

## Commands
To compile the project to native code:
mvn -Pnative package

To test the executable:
run neqsim.exe in the target directory.

## Example
In the folloing example we have established a TEG-process.

https://github.com/equinor/neqsim-native/blob/main/src/main/java/neqsim/process/TestProcess.java

