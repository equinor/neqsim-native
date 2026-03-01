package neqsim.process.python;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * Common utility for running Python process models via GraalPy.
 *
 * <p>This class handles all the boilerplate:
 * <ul>
 *   <li>Loading Python scripts from classpath resources</li>
 *   <li>Creating and managing the GraalPy {@link Context}</li>
 *   <li>Calling a named Python function and extracting results</li>
 *   <li>Thread-safe script caching</li>
 * </ul>
 *
 * <h3>Adding a new Python process model</h3>
 * <ol>
 *   <li>Write your Python script in {@code src/main/resources/python/},
 *       e.g. {@code my_model.py}, with a function that returns a list of
 *       doubles.</li>
 *   <li>Call it from Java with one line:
 *       <pre>
 *       double[] results = PythonProcessRunner.run(
 *           "my_model.py", "my_function", temperature, pressure);
 *       </pre></li>
 *   <li>Add a thin {@code @CEntryPoint} wrapper (see
 *       {@link PythonDewPointProcess} for the minimal pattern).</li>
 * </ol>
 */
public class PythonProcessRunner {

    /** Cached script sources — loaded once per script name. */
    private static final ConcurrentHashMap<String, String> SCRIPT_CACHE =
            new ConcurrentHashMap<>();

    // ----------------------------------------------------------------
    //  Public API
    // ----------------------------------------------------------------

    /**
     * Run a Python function and return the result as a {@code double[]}.
     *
     * <p>The script is loaded from the classpath at
     * {@code /python/<scriptName>}. The named function must return a
     * Python list (or tuple) of numbers.
     *
     * @param scriptName   File name of the Python script under
     *                     {@code src/main/resources/python/},
     *                     e.g. {@code "dewpoint_process.py"}.
     * @param functionName Name of the Python function to call.
     * @param args         Arguments forwarded to the Python function.
     * @return The function's return value converted to {@code double[]}.
     * @throws Exception if loading, parsing, or execution fails.
     * @throws UnsupportedOperationException if the Python runtime is not
     *         available (default build without the {@code with-python} profile).
     */
    public static double[] run(String scriptName, String functionName,
                               Object... args) throws Exception {

        // Fail fast with a clear message when the Python runtime is absent
        // (default builds exclude the GraalPy language runtime).
        if (!isPythonAvailable()) {
            throw new UnsupportedOperationException(
                "Python runtime is not available. "
                + "Build with the 'with-python' Maven profile to enable "
                + "Python process models.");
        }

        String script = loadScript(scriptName);

        try (Context ctx = Context.newBuilder("python")
                .allowAllAccess(true)
                .build()) {

            ctx.eval(Source.newBuilder("python", script, scriptName).build());

            Value fn = ctx.getBindings("python").getMember(functionName);
            if (fn == null || !fn.canExecute()) {
                throw new IllegalArgumentException(
                    "Python function '" + functionName
                    + "' not found or not callable in " + scriptName);
            }

            Value result = fn.execute(args);
            return valueToDoubleArray(result);
        }
    }

    // ----------------------------------------------------------------
    //  Internals
    // ----------------------------------------------------------------

    /** Cached result of the Python availability check. */
    private static volatile Boolean pythonAvailable;

    /**
     * Check whether the GraalPy language runtime is on the classpath.
     * The result is cached after the first call.
     */
    public static boolean isPythonAvailable() {
        if (pythonAvailable == null) {
            synchronized (PythonProcessRunner.class) {
                if (pythonAvailable == null) {
                    try (var engine = org.graalvm.polyglot.Engine.create()) {
                        pythonAvailable = engine.getLanguages().containsKey("python");
                    } catch (Exception e) {
                        pythonAvailable = false;
                    }
                }
            }
        }
        return pythonAvailable;
    }

    /**
     * Load a Python script from the classpath, caching the result.
     */
    private static String loadScript(String scriptName) throws IOException {
        return SCRIPT_CACHE.computeIfAbsent(scriptName, name -> {
            String path = "/python/" + name;
            try (InputStream is = PythonProcessRunner.class.getResourceAsStream(path)) {
                if (is == null) {
                    throw new RuntimeException(
                        "Python script not found on classpath: " + path);
                }
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load Python script: " + path, e);
            }
        });
    }

    /**
     * Convert a Polyglot {@link Value} (list/tuple of numbers) to
     * {@code double[]}.
     */
    private static double[] valueToDoubleArray(Value value) {
        if (value.hasArrayElements()) {
            int len = (int) value.getArraySize();
            double[] result = new double[len];
            for (int i = 0; i < len; i++) {
                result[i] = value.getArrayElement(i).asDouble();
            }
            return result;
        }
        // Single scalar → wrap in array
        return new double[] { value.asDouble() };
    }
}
