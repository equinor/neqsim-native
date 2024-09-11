![NeqSim Logo](https://github.com/equinor/neqsim/blob/master/docs/wiki/neqsimlogocircleflatsmall.png)
# NeqSim-native
This project compiles NeqSim into a native shared library using GraalVM.

## Getting started 
Install the [GraalVM JDK](https://www.graalvm.org/) and read the [getting started documentation](https://www.graalvm.org/latest/docs/getting-started/).

The project is built into native code (eg. shared library/executalble) using the Maven build system (https://maven.apache.org/). All NeqSim build dependencies are given in the pom.xml file. As nesim is not available in a public maven repo, the neqsim librar (jar) has to be added to the local maven repo. You start by donloading the most recent neqsim library, and the add it to the local maven repo using the command:

mvn install:install-file \
   -Dfile=<path-to-file> \
   -DgroupId=<group-id> \
   -DartifactId=<artifact-id> \
   -Dversion=<version> \
   -Dpackaging=<packaging> \
   -DgeneratePom=true

Learn and ask questions in [Discussions for use and development of NeqSim](https://github.com/equinor/neqsim/discussions).

## Commands
To compile the project to native code:
mvn -Pnative package

To test the executable:
run neqsim.exe in the target directory.

## Example
In the folloing example we have established a TEG-process.

https://github.com/equinor/neqsim-native/blob/main/src/main/java/neqsim/process/TestProcess.java

