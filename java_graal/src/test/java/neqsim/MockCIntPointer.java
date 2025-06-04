package neqsim;

import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.SignedWord;

// Simple mock pointer class for demonstration purposes
public class MockCIntPointer implements CIntPointer {
    private int[] values;

    public MockCIntPointer() {
        values = new int[1]; // Example size, adjust as needed
    }

    @Override
    public int read() {
        return values[0];
    }

    @Override
    public void write(int v) {
        values[0] = v;
    }

    @Override
    public int read(int index) {
        return values[index];
    }

    @Override
    public int read(SignedWord index) {
        return values[(int) index.rawValue()];
    }

    @Override
    public void write(int index, int value) {
        values[index] = value;
    }

    @Override
    public void write(SignedWord index, int value) {
        values[(int) index.rawValue()] = value;
    }

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
    public CIntPointer addressOf(int index) {
        throw new UnsupportedOperationException("Unimplemented method 'addressOf'");
    }

    @Override
    public CIntPointer addressOf(SignedWord index) {
        throw new UnsupportedOperationException("Unimplemented method 'addressOf'");
    }
}
