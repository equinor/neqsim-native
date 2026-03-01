# 32-bit NeqSim Stub DLL

This directory contains the source code for a thin **32-bit stub DLL** that enables 32-bit Windows applications to call NeqSim process simulations compiled as 64-bit GraalVM native images.

## Architecture

```
┌──────────────────┐       TCP 127.0.0.1:19876       ┌──────────────────────────┐
│  32-bit Caller   │  ──→  neqsim.dll (32-bit stub)  │                          │
│  (your_app.exe)  │       packs args, sends request  │  neqsim_server.exe       │
│                  │  ←──  unpacks response            │  (64-bit GraalVM EXE)    │
└──────────────────┘                                  │  runs NeqSim models      │
                                                      └──────────────────────────┘
```

GraalVM `native-image` only produces 64-bit binaries. This two-process architecture bridges the gap so that 32-bit applications can use NeqSim with **zero source code changes**:

1. **`neqsim_server.exe`** — A 64-bit GraalVM native-image executable that hosts the real NeqSim process models and listens on TCP port 19876.
2. **`neqsim.dll`** (32-bit) — A thin C stub that exports the **same function signatures** as the original 64-bit GraalVM-produced DLL. It forwards calls to the server over a local TCP socket using a binary protocol.

## Quick Start

### 1. Get the files

Download `neqsim-windows-x86.zip` from the [Releases page](https://github.com/equinor/neqsim-native/releases). The archive contains:

| File | Description |
|------|-------------|
| `neqsim.dll` | 32-bit stub DLL |
| `neqsim.lib` | Import library (for build-time linking) |
| `neqsim.h` | C/C++ header |
| `neqsim_server.exe` | 64-bit NeqSim server (~80 MB) |
| `README.md` | This documentation |

### 2. Place files alongside your application

```
your_app/
├── your_app.exe          (32-bit application)
├── neqsim.dll            (32-bit stub)
└── neqsim_server.exe     (64-bit server)
```

> `neqsim.lib` and `neqsim.h` are only needed at compile/link time, not at runtime.

### 3. Use exactly like the 64-bit DLL

The stub DLL exports the same functions with identical signatures. Existing code that links against the 64-bit `neqsim.dll` works without changes:

```cpp
#include "neqsim.h"   // or "neqsim_stub.h" — same API

int main() {
    graal_isolate_t* isolate = nullptr;
    graal_isolatethread_t* thread = nullptr;

    // Creates TCP connection to server (auto-launches neqsim_server.exe)
    int rc = graal_create_isolate(nullptr, &isolate, &thread);
    if (rc != 0) return rc;

    // --- Water Dew Point ---
    double dew_point = 0.0;
    int quality = 0;
    calcWaterDewPoint(thread, 50.0, 100.0, &dew_point, &quality);

    // --- Water in Gas ---
    double water_content = 0.0;
    int wig_quality = 0;
    calcWaterInGas(thread, 50.0, 25.0, &water_content, &wig_quality);

    // --- Python Dew Point (if server built with Python support) ---
    double py_dew_point = 0.0, gas_density = 0.0;
    int py_quality = 0;
    PY_run_dewpoint_calculation(thread,
        25.0, 50.0, 100000.0,
        &py_dew_point, &gas_density, &py_quality);

    // Clean up (closes TCP connection)
    graal_tear_down_isolate(thread);
    return 0;
}
```

### 4. Compile your application (32-bit)

```bat
cl /nologo /EHsc /W4 your_app.cpp neqsim.lib /Fe:your_app.exe
```

Make sure you are in a **32-bit** Developer Command Prompt (or ran `vcvarsall.bat x86`).

## API Reference

The stub DLL exports the exact same functions as the 64-bit `neqsim.dll`:

| Function | Description |
|---|---|
| `graal_create_isolate()` | Connects to server (auto-launches `neqsim_server.exe` if needed) |
| `graal_detach_thread()` | No-op (keeps connection alive for API compatibility) |
| `graal_tear_down_isolate()` | Closes the TCP connection |
| `calcWaterDewPoint()` | Water dew point temperature calculation |
| `calcWaterInGas()` | Water content in gas calculation |
| `PY_run_dewpoint_calculation()` | Python-based hydrocarbon dew point calculation |

For detailed parameter descriptions, see the [API documentation](../doc/README.md).

## Server Auto-Launch Behavior

When `graal_create_isolate()` is called:

1. The stub attempts to connect to `127.0.0.1:19876`.
2. If the connection fails, it looks for `neqsim_server.exe` in the **same directory as the DLL**.
3. It launches the server as a background process and waits up to 30 seconds for it to become available.
4. Once connected, all subsequent function calls reuse the same TCP connection.
5. When `graal_tear_down_isolate()` is called (or the process exits), the connection is closed. The server process continues running and can serve other clients.

> **Tip:** You can also start the server manually before your application:
> ```bat
> neqsim_server.exe
> ```
> This is useful during development or when multiple 32-bit applications need to share the same server.

## Building from Source

### Prerequisites

- **Visual Studio Build Tools 2022** with C++ desktop workload
- **GraalVM JDK 25** with `native-image` on PATH (only needed for server EXE)

### Build script

From a Developer Command Prompt:

```bat
REM Build both server EXE and 32-bit stub DLL
build.bat

REM Build only the 32-bit stub DLL (fast, no GraalVM needed)
build.bat stub

REM Build only the 64-bit server EXE (requires GraalVM)
build.bat server
```

Output files are placed in `stub32\dist\`:

| File | Description |
|------|-------------|
| `dist\neqsim_server.exe` | 64-bit GraalVM native-image server (~80 MB) |
| `dist\neqsim.dll` | 32-bit stub DLL |
| `dist\neqsim.lib` | Import library |

## Binary Protocol

The stub communicates with the server using a simple little-endian binary protocol over TCP:

```
REQUEST:   [uint32 func_id] [uint32 payload_len] [byte[] payload]
RESPONSE:  [uint32 resp_len] [byte[] response]
```

| Function ID | Function |
|---|---|
| 1 | `calcWaterDewPoint` |
| 2 | `calcWaterInGas` |
| 3 | `PY_run_dewpoint_calculation` |
| 99 | PING (internal health check) |

Payloads contain packed `double` (8 bytes) and `int` (4 bytes) values matching each function's parameters, in declaration order. Responses contain packed output values followed by the quality `int`.

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|----------|
| `graal_create_isolate` returns non-zero | Server not found or port blocked | Ensure `neqsim_server.exe` is in the same directory as `neqsim.dll`. Check that port 19876 is not blocked by a firewall. |
| Application hangs on first call | Server startup is slow | The server may take 5-10 seconds on first launch. Subsequent calls are fast. |
| `quality` output is 0 | Simulation failed | Check input parameter ranges. See [API documentation](../doc/README.md) for valid ranges. |
| Antivirus blocks `neqsim_server.exe` | Unsigned executable | Whitelist the executable in your antivirus software. |
| Port 19876 already in use | Another server instance running | Kill the existing process, or let the stub connect to the running instance (this is fine). |

## Source Files

| File | Description |
|---|---|
| `neqsim_stub.c` | Stub DLL implementation (TCP client, auto-launch, RPC) |
| `neqsim_stub.h` | Public C/C++ header (drop-in for `neqsim.h`) |
| `neqsim_stub.def` | DLL export definitions |
| `build.bat` | Build script for server + stub |
| `README.md` | This documentation |
