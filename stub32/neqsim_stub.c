/**
 * neqsim_stub.c — Thin 32-bit stub DLL that forwards NeqSim calls
 *                  to the 64-bit neqsim_server.exe over TCP (localhost).
 *
 * Architecture:
 *   32-bit caller  -->  neqsim_stub.dll (this)  -->  TCP 127.0.0.1:19876
 *                                                        |
 *                                                   neqsim_server.exe (64-bit GraalVM native image)
 *
 * Protocol (little-endian):
 *   REQUEST:   uint32 func_id | uint32 payload_len | byte[payload_len]
 *   RESPONSE:  uint32 payload_len | byte[payload_len]
 *
 * The stub auto-launches neqsim_server.exe if it is not already running.
 *
 * Build (32-bit):
 *   cl /LD /D NEQSIM_STUB_EXPORTS neqsim_stub.c ws2_32.lib /Fe:neqsim.dll
 */

#define WIN32_LEAN_AND_MEAN
#define _WINSOCK_DEPRECATED_NO_WARNINGS
#define _CRT_SECURE_NO_WARNINGS
#ifndef NEQSIM_STUB_EXPORTS
#define NEQSIM_STUB_EXPORTS
#endif
#include <windows.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "neqsim_stub.h"

#pragma comment(lib, "ws2_32.lib")

/* ------------------------------------------------------------------ */
/* Constants                                                            */
/* ------------------------------------------------------------------ */
#define NEQSIM_SERVER_PORT  19876
#define NEQSIM_SERVER_HOST  "127.0.0.1"

/* Function IDs — must match NeqSimPipeServer.java */
#define FUNC_CALC_WATER_DEW_POINT  1
#define FUNC_CALC_WATER_IN_GAS     2
#define FUNC_PY_DEWPOINT           3
#define FUNC_PING                  99

/* ------------------------------------------------------------------ */
/* Globals                                                              */
/* ------------------------------------------------------------------ */
static SOCKET  g_sock = INVALID_SOCKET;
static int     g_wsa_initialised = 0;
static HANDLE  g_server_process = NULL;
static CRITICAL_SECTION g_cs;
static int     g_cs_initialised = 0;

/* ------------------------------------------------------------------ */
/* Forward declarations                                                 */
/* ------------------------------------------------------------------ */
static int  connect_to_server(void);
static int  ensure_connection(void);
static void close_connection(void);
static int  send_all(const char* buf, int len);
static int  recv_all(char* buf, int len);
static int  launch_server(void);
static int  wait_for_server(int timeout_ms);

/* ------------------------------------------------------------------ */
/* DLL entry point                                                      */
/* ------------------------------------------------------------------ */
BOOL APIENTRY DllMain(HMODULE hModule, DWORD reason, LPVOID reserved)
{
    (void)hModule; (void)reserved;
    switch (reason) {
    case DLL_PROCESS_ATTACH:
        InitializeCriticalSection(&g_cs);
        g_cs_initialised = 1;
        break;
    case DLL_PROCESS_DETACH:
        close_connection();
        if (g_cs_initialised) {
            DeleteCriticalSection(&g_cs);
            g_cs_initialised = 0;
        }
        break;
    }
    return TRUE;
}

/* ------------------------------------------------------------------ */
/* Low-level network helpers                                            */
/* ------------------------------------------------------------------ */
static int init_wsa(void)
{
    if (!g_wsa_initialised) {
        WSADATA wsa;
        if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0)
            return -1;
        g_wsa_initialised = 1;
    }
    return 0;
}

static int send_all(const char* buf, int len)
{
    int total = 0;
    while (total < len) {
        int n = send(g_sock, buf + total, len - total, 0);
        if (n <= 0) return -1;
        total += n;
    }
    return 0;
}

static int recv_all(char* buf, int len)
{
    int total = 0;
    while (total < len) {
        int n = recv(g_sock, buf + total, len - total, 0);
        if (n <= 0) return -1;
        total += n;
    }
    return 0;
}

static void close_socket(void)
{
    if (g_sock != INVALID_SOCKET) {
        closesocket(g_sock);
        g_sock = INVALID_SOCKET;
    }
}

static void close_connection(void)
{
    close_socket();
    if (g_wsa_initialised) {
        WSACleanup();
        g_wsa_initialised = 0;
    }
}

/* ------------------------------------------------------------------ */
/* Connect to server, launching if needed                               */
/* ------------------------------------------------------------------ */
static int connect_to_server(void)
{
    struct sockaddr_in addr;

    if (init_wsa() != 0) return -1;

    g_sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (g_sock == INVALID_SOCKET) return -1;

    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(NEQSIM_SERVER_PORT);
    addr.sin_addr.s_addr = inet_addr(NEQSIM_SERVER_HOST);

    if (connect(g_sock, (struct sockaddr*)&addr, sizeof(addr)) != 0) {
        closesocket(g_sock);
        g_sock = INVALID_SOCKET;
        return -1;
    }
    return 0;
}

static int ensure_connection(void)
{
    if (g_sock != INVALID_SOCKET) return 0;

    /* Try direct connect first (server may already be running) */
    if (connect_to_server() == 0) return 0;

    /* Launch the server and retry */
    if (launch_server() != 0) return -1;
    if (wait_for_server(30000) != 0) return -1;
    /* wait_for_server leaves g_sock connected — no need to reconnect */
    return 0;
}

/* ------------------------------------------------------------------ */
/* Server lifecycle                                                     */
/* ------------------------------------------------------------------ */
static int launch_server(void)
{
    STARTUPINFOA si;
    PROCESS_INFORMATION pi;
    char cmd[MAX_PATH + 64];
    char dir[MAX_PATH];

    /* Look for neqsim_server.exe next to this DLL */
    HMODULE hm = NULL;
    GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS |
                       GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                       (LPCSTR)&launch_server, &hm);
    GetModuleFileNameA(hm, dir, MAX_PATH);
    /* Strip filename to get directory */
    {
        char* p = strrchr(dir, '\\');
        if (p) *(p + 1) = '\0';
    }

    _snprintf(cmd, sizeof(cmd), "\"%sneqsim_server.exe\"", dir);
    cmd[sizeof(cmd) - 1] = '\0';  /* _snprintf may not null-terminate */

    memset(&si, 0, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;

    memset(&pi, 0, sizeof(pi));

    if (!CreateProcessA(NULL, cmd, NULL, NULL, FALSE,
                        CREATE_NO_WINDOW, NULL, dir, &si, &pi)) {
        return -1;
    }

    g_server_process = pi.hProcess;
    CloseHandle(pi.hThread);
    return 0;
}

static int wait_for_server(int timeout_ms)
{
    int elapsed = 0;
    while (elapsed < timeout_ms) {
        if (connect_to_server() == 0) return 0;
        Sleep(500);
        elapsed += 500;
    }
    return -1;
}

/* ------------------------------------------------------------------ */
/* Generic RPC call                                                     */
/* ------------------------------------------------------------------ */

/**
 * Send a request and receive a response.
 *
 * @param func_id       Function identifier.
 * @param payload       Input payload bytes (little-endian packed).
 * @param payload_len   Length of input payload.
 * @param response      Buffer to receive output payload.
 * @param response_cap  Capacity of response buffer.
 * @param response_len  Actual response length written.
 * @return 0 on success, -1 on error.
 */
static int rpc_call(int func_id, const char* payload, int payload_len,
                    char* response, int response_cap, int* response_len)
{
    unsigned int header[2];
    unsigned int resp_len;

    EnterCriticalSection(&g_cs);

    if (ensure_connection() != 0) {
        LeaveCriticalSection(&g_cs);
        return -1;
    }

    /* Send request header */
    header[0] = (unsigned int)func_id;
    header[1] = (unsigned int)payload_len;
    if (send_all((const char*)header, 8) != 0) goto fail;

    /* Send payload */
    if (payload_len > 0) {
        if (send_all(payload, payload_len) != 0) goto fail;
    }

    /* Receive response length */
    if (recv_all((char*)&resp_len, 4) != 0) goto fail;

    if ((int)resp_len > response_cap) goto fail;

    /* Receive response payload */
    if (resp_len > 0) {
        if (recv_all(response, (int)resp_len) != 0) goto fail;
    }

    *response_len = (int)resp_len;
    LeaveCriticalSection(&g_cs);
    return 0;

fail:
    close_socket();
    LeaveCriticalSection(&g_cs);
    return -1;
}

/* ------------------------------------------------------------------ */
/* Helper: read a little-endian double from a buffer                    */
/* ------------------------------------------------------------------ */
static double read_double(const char* buf, int offset)
{
    double val;
    memcpy(&val, buf + offset, sizeof(double));
    return val;
}

static int read_int(const char* buf, int offset)
{
    int val;
    memcpy(&val, buf + offset, sizeof(int));
    return val;
}

/* ================================================================== */
/* Public API — GraalVM-compatible signatures                          */
/* ================================================================== */

NEQSIM_API int graal_create_isolate(
    void*                  params,
    graal_isolate_t*       isolate,
    graal_isolatethread_t* thread)
{
    (void)params;

    /* Ensure we can connect to (or launch) the server */
    EnterCriticalSection(&g_cs);
    int rc = ensure_connection();
    LeaveCriticalSection(&g_cs);

    if (rc != 0) return -1;

    /* Set dummy handles for API compatibility */
    if (isolate) *isolate = (graal_isolate_t)(intptr_t)1;
    if (thread)  *thread  = (graal_isolatethread_t)(intptr_t)1;
    return 0;
}

NEQSIM_API int graal_detach_thread(graal_isolatethread_t thread)
{
    (void)thread;
    /* Keep connection alive — will be cleaned up in DLL_PROCESS_DETACH */
    return 0;
}

NEQSIM_API int graal_tear_down_isolate(graal_isolatethread_t thread)
{
    (void)thread;
    close_connection();
    return 0;
}

/* ------------------------------------------------------------------ */
/* calcWaterDewPoint                                                    */
/* ------------------------------------------------------------------ */
NEQSIM_API void calcWaterDewPoint(
    graal_isolatethread_t thread,
    double  pressure,
    double  ppmWater,
    double* result,
    int*    quality)
{
    /* Payload: 2 doubles = 16 bytes */
    char payload[16];
    char response[256];
    int  resp_len = 0;
    int  offset = 0;

    (void)thread;

    memcpy(payload + offset, &pressure, 8);   offset += 8;
    memcpy(payload + offset, &ppmWater, 8);   offset += 8;

    if (rpc_call(FUNC_CALC_WATER_DEW_POINT, payload, offset,
                 response, sizeof(response), &resp_len) != 0
        || resp_len < 12) {
        if (quality) *quality = 0;
        return;
    }

    /* Response: 1 double + 1 int = 12 bytes */
    if (result)  *result  = read_double(response, 0);
    if (quality) *quality = read_int(response, 8);
}

/* ------------------------------------------------------------------ */
/* calcWaterInGas                                                       */
/* ------------------------------------------------------------------ */
NEQSIM_API void calcWaterInGas(
    graal_isolatethread_t thread,
    double  pressure,
    double  temperature,
    double* result,
    int*    quality)
{
    /* Payload: 2 doubles = 16 bytes */
    char payload[16];
    char response[256];
    int  resp_len = 0;
    int  offset = 0;

    (void)thread;

    memcpy(payload + offset, &pressure, 8);     offset += 8;
    memcpy(payload + offset, &temperature, 8);  offset += 8;

    if (rpc_call(FUNC_CALC_WATER_IN_GAS, payload, offset,
                 response, sizeof(response), &resp_len) != 0
        || resp_len < 12) {
        if (quality) *quality = 0;
        return;
    }

    /* Response: 1 double + 1 int = 12 bytes */
    if (result)  *result  = read_double(response, 0);
    if (quality) *quality = read_int(response, 8);
}

/* ------------------------------------------------------------------ */
/* PY_run_dewpoint_calculation                                          */
/* ------------------------------------------------------------------ */
NEQSIM_API void PY_run_dewpoint_calculation(
    graal_isolatethread_t thread,
    double  temperature,
    double  pressure,
    double  gas_flow_rate,
    double* dew_point_temperature,
    double* gas_density,
    int*    quality)
{
    /* Payload: 3 doubles = 24 bytes */
    char payload[24];
    char response[256];
    int  resp_len = 0;
    int  offset = 0;

    (void)thread;

    memcpy(payload + offset, &temperature, 8);     offset += 8;
    memcpy(payload + offset, &pressure, 8);        offset += 8;
    memcpy(payload + offset, &gas_flow_rate, 8);   offset += 8;

    if (rpc_call(FUNC_PY_DEWPOINT, payload, offset,
                 response, sizeof(response), &resp_len) != 0
        || resp_len < 20) {
        if (quality) *quality = 0;
        return;
    }

    /* Response: 2 doubles + 1 int = 20 bytes */
    if (dew_point_temperature) *dew_point_temperature = read_double(response, 0);
    if (gas_density)           *gas_density           = read_double(response, 8);
    if (quality)               *quality               = read_int(response, 16);
}
