# How to compile:

change to this directory

g++ -o water_dew_point water_dew_point.cpp -I/workspaces/neqsim-native/java_graal/target -L/workspaces/neqsim-native/java_graal/target -Wl,-rpath,/workspaces/neqsim-native/java_graal/target -l:neqsim.so -ldl -lpthread

Run it with ./water_dew_point

