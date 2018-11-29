package jwp.fuzz;

import java.util.stream.BaseStream;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class SeedContainer {
    private final int[] intSeeds;
    private final long[] longSeeds;
    private final double[] doubleSeeds;
    private Class type;

    private boolean isEmpty() {
        return intSeeds == null && longSeeds == null && doubleSeeds == null;
    }

    public SeedContainer() {
        intSeeds = null;
        longSeeds = null;
        doubleSeeds = null;
    }

    public SeedContainer(int... seeds) {
        intSeeds = seeds;
        longSeeds = null;
        doubleSeeds = null;
        type = Integer.TYPE;
    }


    public SeedContainer(long... seeds) {
        longSeeds = seeds;
        intSeeds = null;
        doubleSeeds = null;
        type = Long.TYPE;
    }

    public SeedContainer(double... seeds) {
        doubleSeeds = seeds;
        longSeeds = null;
        intSeeds = null;
        type = Double.TYPE;
    }

    public IntStream getIntSeeds() {
        if(isEmpty()) {
            return IntStream.empty();
        }
        if(type != Integer.TYPE) {
            throw new IllegalStateException("Invalid type");
        }
        return IntStream.of(intSeeds);
    }

    public DoubleStream getDoubleSeeds() {
        if(isEmpty()) {
            return DoubleStream.empty();
        }
        if(type != Double.TYPE) {
            throw new IllegalStateException("Invalid type");
        }
        return DoubleStream.of(doubleSeeds);
    }

    public LongStream getLongSeeds() {
        if(isEmpty()) {
            return LongStream.empty();
        }
        if(type != Long.TYPE) {
            throw new IllegalStateException("Invalid type");
        }
        return LongStream.of(longSeeds);
    }

    public BaseStream getSeeds() {
        if(isEmpty()) {
            return IntStream.empty();
        }
        if(type == Integer.TYPE) {
            return getIntSeeds();
        }
        if(type == Long.TYPE) {
            return getLongSeeds();
        }
        if(type == Double.TYPE) {
            return  getDoubleSeeds();
        }
        throw new IllegalStateException("Invalid State");
    }

    public static SeedContainer empty() {
        return new SeedContainer();
    }

}
