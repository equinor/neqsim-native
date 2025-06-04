package neqsim;

import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.SignedWord;

// Simple mock pointer class for demonstration purposes
// In a real scenario, you'd obtain appropriate CDoublePointer 
// instances from Graal or a relevant library.
public class MockCDoublePointer implements CDoublePointer {
    private double[] values;

    public MockCDoublePointer() {
        values = new double[1]; 
    }

    @Override
    public double read() {
        return values[0];
    }

    @Override
    public void write(double v) {
        values[0] = v;
    }

    @Override
    public double read(int index) {
        return values[index];
    }

    @Override
    public double read(SignedWord index) {
        return values[(int) index.rawValue()];
    }

    @Override
    public void write(int index, double value) {
        values[index] = value;
    }

    @Override
    public void write(SignedWord index, double value) {
        values[(int) index.rawValue()] = value;
    }

    // For safety/compatibility, but not strictly necessary here
    @Override
    public boolean isNull() {
        throw new UnsupportedOperationException("Unimplemented method 'isNull'");
    }

    @Override
    public boolean isNonNull() {
        throw new UnsupportedOperationException("Unimplemented method 'isNonNull'");
    }

    @Override
    public boolean equal(ComparableWord val) {
        throw new UnsupportedOperationException("Unimplemented method 'equal'");
    }

    @Override
    public boolean notEqual(ComparableWord val) {
        throw new UnsupportedOperationException("Unimplemented method 'notEqual'");
    }

    @Override
    public long rawValue() {
        throw new UnsupportedOperationException("Unimplemented method 'rawValue'");
    }

    @Override
    public CDoublePointer addressOf(int index) {
        throw new UnsupportedOperationException("Unimplemented method 'addressOf'");
    }

    @Override
    public CDoublePointer addressOf(SignedWord index) {
        throw new UnsupportedOperationException("Unimplemented method 'addressOf'");
    }
}