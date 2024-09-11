![NeqSim Logo](https://github.com/equinor/neqsim/blob/master/docs/wiki/neqsimlogocircleflatsmall.png)
# NeqSim Native
This project compiles NeqSim simulation models into a native executable or shared library using GraalVM that can be used directly or integrated into eg. C/C++ programs. An example of use is implementation of NeqSim models in process control systems.

## Getting Started
1. Install the [GraalVM JDK](https://www.graalvm.org/)
2. Review the [GraalVM Getting Started Guide](https://www.graalvm.org/latest/docs/getting-started/)
3. Review [native compilation documentation](https://www.graalvm.org/latest/reference-manual/native-image/) with GraalVM

The project is built into native code (e.g., shared libraries or executables) using the Maven build system. All NeqSim dependencies are specified in the `pom.xml` file. Since NeqSim is not available in a public Maven repository, you'll need to manually add the NeqSim JAR to your local Maven repository. Start by downloading the latest version of the NeqSim library, then run the following command to add it to your local Maven repo:

```bash
mvn org.apache.maven.plugins:maven-install-plugin:install-file -Dfile="C:\temp\neqsim-2.5.32.jar
```

Learn and ask questions in [Discussions for use and development of NeqSim](https://github.com/equinor/neqsim/discussions).

## Commands
To compile the project to native code:
```
mvn -Pnative package
```
At the moment the executable is created from the class [TestProcess](https://github.com/equinor/neqsim-native/blob/main/src/main/java/neqsim/process/TestProcess.java). 

To test the executable:
run neqsim.exe in the target directory.

To create a shared library - activate this in the pom file.
C:\Users\esol
The shared libraries and header files can be integrated into third party C/C++ programs.

## Example
In the following example we have established a TEG-process.

https://github.com/equinor/neqsim-native/blob/main/src/main/java/neqsim/process/TestProcess.java

