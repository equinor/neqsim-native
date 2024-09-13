![NeqSim Logo](https://github.com/equinor/neqsim/blob/master/docs/wiki/neqsimlogocircleflatsmall.png)
# NeqSim Native
This project compiles NeqSim simulation models into a native executable or shared library using GraalVM that can be used directly or integrated into eg. C/C++ programs. An example of use is implementation of NeqSim models in process control systems.

## Getting Started
1. Install the [GraalVM JDK](https://www.graalvm.org/). On Linux use: sdk install java 22.3.r17-grl and sdk use java 22.3.r17-grl
2. Review the [GraalVM Getting Started Guide](https://www.graalvm.org/latest/docs/getting-started/)
3. Review [native compilation documentation](https://www.graalvm.org/latest/reference-manual/native-image/) with GraalVM

The project is built into native code (e.g., shared libraries or executables) using the Maven build system. All NeqSim dependencies are specified in the `pom.xml` file. Since NeqSim is not available in a public Maven repository, you'll need to manually add the NeqSim JAR to your local Maven repository. Start by downloading the [latest release](https://github.com/equinor/neqsim/releases) of the NeqSim jar (neqsim-X.X.X.jar). Using Curl you can do it by running: 
```bash
curl -L -o neqsim-2.5.34.jar https://github.com/equinor/neqsim/releases/download/v2.5.34/neqsim-2.5.34.jar
```
then run the following command to add it to your local Maven repo:
```bash
./mvnw org.apache.maven.plugins:maven-install-plugin:install-file -Dfile="neqsim-2.5.34.jar"
```

Learn and ask questions in [Discussions for use and development of NeqSim](https://github.com/equinor/neqsim/discussions).

## Commands
To compile the project to native code (on windows use mvnw.cmd):

```
./mvnw -Pnative package
```

At the moment the executable is created from the class [TestProcess](https://github.com/equinor/neqsim-native/blob/main/src/main/java/neqsim/process/TestProcess.java). 

To test the executable:
run neqsim.exe in the target directory.

To create a shared library - activate this in the pom file by specifying the configuration:
```
<sharedLibrary>true</sharedLibrary>
```
See [documentation](https://www.graalvm.org/latest/reference-manual/native-image/guides/build-native-shared-library/).
The shared libraries and header files can be integrated into third party C/C++ programs.

## Example
In the following example we have established a TEG-process.

https://github.com/equinor/neqsim-native/blob/main/src/main/java/neqsim/process/TestProcess.java

This code can be used from a c++ program as illustrated in the [example code](https://github.com/equinor/neqsim-native/blob/main/example/main.cpp).

