# How to Compile

All examples link against the `neqsim.so` shared library built from the `java_graal/` directory. Adjust the paths below to match your checkout location.

```bash
# Set this to your neqsim-native checkout root
NEQSIM_ROOT=$(cd ../../ && pwd)
TARGET=$NEQSIM_ROOT/java_graal/target
```

## Water Dew Point (Java)

```bash
g++ -o water_dew_point water_dew_point.cpp -I$TARGET -L$TARGET -Wl,-rpath,$TARGET -l:neqsim.so -ldl -lpthread
./water_dew_point
```

## Python Dew Point (via GraalPy)

> **Note:** Requires a **with-python** build (`-Pnative-linux,with-python`). Default builds will return `quality=0`.

```bash
g++ -o py_dewpoint_calc py_dewpoint_calc.cpp -I$TARGET -L$TARGET -Wl,-rpath,$TARGET -l:neqsim.so -ldl -lpthread
./py_dewpoint_calc
```

