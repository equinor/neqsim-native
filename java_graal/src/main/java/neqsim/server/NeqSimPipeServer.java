package neqsim.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * NeqSim TCP server.
 *
 * <p>Hosts the 64-bit NeqSim process models and exposes them over a simple
 * binary protocol so that a thin 32-bit stub DLL can forward calls from
 * legacy 32-bit callers.
 *
 * <h3>Protocol (little-endian)</h3>
 * <pre>
 * REQUEST
 *   uint32  function_id   (1 = calcWaterDewPoint, 2 = calcWaterInGas,
 *                           3 = PY_run_dewpoint_calculation, 99 = PING)
 *   uint32  payload_bytes (number of bytes that follow)
 *   byte[]  payload       (packed doubles / ints, function-specific)
 *
 * RESPONSE
 *   uint32  payload_bytes
 *   byte[]  payload       (packed output doubles / ints)
 * </pre>
 *
 * <p>The server listens on TCP port 19876 (localhost only).
 */
public class NeqSimPipeServer {

    /** Function IDs — keep in sync with neqsim_stub.c */
    static final int FUNC_CALC_WATER_DEW_POINT = 1;
    static final int FUNC_CALC_WATER_IN_GAS = 2;
    static final int FUNC_PY_DEWPOINT = 3;
    static final int FUNC_PING = 99;

    static final int TCP_PORT = 19876;

    private volatile boolean running = true;

    // ---------------------------------------------------------------
    // Entry point
    // ---------------------------------------------------------------
    public static void main(String[] args) {
        System.out.println("[neqsim-server] Starting NeqSim server on port " + TCP_PORT);
        new NeqSimPipeServer().runTcpLoop();
    }

    // ---------------------------------------------------------------
    // TCP mode
    // ---------------------------------------------------------------
    private void runTcpLoop() {
        try (ServerSocket ss = new ServerSocket(TCP_PORT, 50,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            System.out.println("[neqsim-server] Listening on 127.0.0.1:" + TCP_PORT);
            while (running) {
                Socket client = ss.accept();
                System.out.println("[neqsim-server] Client connected: " + client.getRemoteSocketAddress());
                // Handle each client in a new thread
                Thread t = new Thread(() -> {
                    try {
                        handleConnection(client.getInputStream(), client.getOutputStream());
                    } catch (IOException e) {
                        System.err.println("[neqsim-server] Connection error: " + e.getMessage());
                    } finally {
                        try { client.close(); } catch (IOException ignored) {}
                    }
                });
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e) {
            System.err.println("[neqsim-server] Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ---------------------------------------------------------------
    // Connection handler — reads requests, dispatches, writes response
    // ---------------------------------------------------------------
    private void handleConnection(InputStream rawIn, OutputStream rawOut) throws IOException {
        DataInputStream in = new DataInputStream(rawIn);
        DataOutputStream out = new DataOutputStream(rawOut);

        // A single connection can carry multiple sequential requests
        // (the stub keeps the connection alive for performance).
        try {
            while (true) {
                int funcId = readInt32LE(in);
                int payloadLen = readInt32LE(in);
                if (payloadLen < 0 || payloadLen > 65536) {
                    System.err.println("[neqsim-server] invalid payload length: " + payloadLen);
                    break;
                }
                byte[] payload = new byte[payloadLen];
                in.readFully(payload);

                byte[] response = dispatch(funcId, payload);

                writeInt32LE(out, response.length);
                out.write(response);
                out.flush();
            }
        } catch (java.io.EOFException e) {
            // Client disconnected — normal
        }
    }

    // ---------------------------------------------------------------
    // Dispatcher
    // ---------------------------------------------------------------
    private byte[] dispatch(int funcId, byte[] payload) {
        try {
            switch (funcId) {
                case FUNC_CALC_WATER_DEW_POINT:
                    return handleCalcWaterDewPoint(payload);
                case FUNC_CALC_WATER_IN_GAS:
                    return handleCalcWaterInGas(payload);
                case FUNC_PY_DEWPOINT:
                    return handlePyDewpoint(payload);
                case FUNC_PING:
                    return handlePing();
                default:
                    System.err.println("[neqsim-server] unknown function id: " + funcId);
                    return new byte[0];
            }
        } catch (Exception e) {
            System.err.println("[neqsim-server] error in function " + funcId + ": " + e.getMessage());
            e.printStackTrace();
            return new byte[0];
        }
    }

    // ---------------------------------------------------------------
    // PING  (for health-check / connection test)
    // ---------------------------------------------------------------
    private byte[] handlePing() {
        // Return 1 int (quality = 1)
        return packResponse(new double[0], 1);
    }

    // ---------------------------------------------------------------
    // calcWaterDewPoint
    //   Input:  double[2] (pressure, ppmWater)
    //   Output: double[1] (dew_point_temperature), int quality
    // ---------------------------------------------------------------
    private byte[] handleCalcWaterDewPoint(byte[] payload) {
        java.nio.ByteBuffer buf = wrap(payload);
        double pressure = buf.getDouble();
        double ppmWater = buf.getDouble();

        double[] outDoubles = new double[1];
        int quality = 0;
        try {
            quality = ProcessDispatcher.runCalcWaterDewPoint(
                    pressure, ppmWater, outDoubles);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return packResponse(outDoubles, quality);
    }

    // ---------------------------------------------------------------
    // calcWaterInGas
    //   Input:  double[2] (pressure, temperature)
    //   Output: double[1] (water_content_ppm), int quality
    // ---------------------------------------------------------------
    private byte[] handleCalcWaterInGas(byte[] payload) {
        java.nio.ByteBuffer buf = wrap(payload);
        double pressure = buf.getDouble();
        double temperature = buf.getDouble();

        double[] outDoubles = new double[1];
        int quality = 0;
        try {
            quality = ProcessDispatcher.runCalcWaterInGas(
                    pressure, temperature, outDoubles);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return packResponse(outDoubles, quality);
    }

    // ---------------------------------------------------------------
    // PY_run_dewpoint_calculation
    //   Input:  double[3] (temperature, pressure, gas_flow_rate)
    //   Output: double[2] (dew_point_temperature, gas_density),
    //           int quality
    // ---------------------------------------------------------------
    private byte[] handlePyDewpoint(byte[] payload) {
        java.nio.ByteBuffer buf = wrap(payload);
        double temperature = buf.getDouble();
        double pressure = buf.getDouble();
        double gasFlowRate = buf.getDouble();

        double[] outDoubles = new double[2];
        int quality = 0;
        try {
            quality = ProcessDispatcher.runPyDewpoint(
                    temperature, pressure, gasFlowRate, outDoubles);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return packResponse(outDoubles, quality);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private static java.nio.ByteBuffer wrap(byte[] data) {
        return java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Pack output doubles + quality int into a little-endian byte[].
     */
    static byte[] packResponse(double[] doubles, int quality) {
        int size = doubles.length * 8 + 4;
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (double d : doubles) {
            buf.putDouble(d);
        }
        buf.putInt(quality);
        return buf.array();
    }

    /** Read a 32-bit little-endian int from a DataInputStream. */
    private static int readInt32LE(DataInputStream in) throws IOException {
        byte[] b = new byte[4];
        in.readFully(b);
        return (b[0] & 0xFF) | ((b[1] & 0xFF) << 8) | ((b[2] & 0xFF) << 16) | ((b[3] & 0xFF) << 24);
    }

    /** Write a 32-bit little-endian int to a DataOutputStream. */
    private static void writeInt32LE(DataOutputStream out, int val) throws IOException {
        out.write(val & 0xFF);
        out.write((val >> 8) & 0xFF);
        out.write((val >> 16) & 0xFF);
        out.write((val >> 24) & 0xFF);
    }
}
