package org.abs_models.frontend.typechecker.nullable;

import org.abs_models.backend.common.InternalBackendException;
import org.abs_models.frontend.ast.Model;
import org.abs_models.frontend.parser.Main;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;

public class Benchmark {
    private static final int RUNS = 10000;

    public static void main(String[] args) {
        long[] times = times();
        double avg = Arrays.stream(times).asDoubleStream().average().orElse(0.0);
        double median;
        Arrays.sort(times);
        if (times.length % 2 == 0)
            median = ((double) times[times.length / 2] + (double) times[times.length / 2 - 1]) / 2;
        else
            median = (double) times[times.length / 2];
        System.out.println("\nAverage run time: " + avg + "ms");
        System.out.println("Median run time: " + median + "ms");
    }

    private static long[] times() {
        long[] times = new long[RUNS];

        for (int i = 0; i < RUNS; i++) {
            System.out.println("Run " + i + " of " + RUNS);
            times[i] = timedRun();
        }

        return times;
    }

    private static long timedRun() {
        long start = System.currentTimeMillis();
        run();
        long end = System.currentTimeMillis();
        return end - start;
    }

    public static void run() {
        Model m = parse("module M; {skip;}");
        assert m != null;
        m.registerTypeSystemExtension(new NullCheckerExtension(m));
        m.typeCheck();
    }

    private static Model parse(String s) {
        try {
            return Main.parse(null, new StringReader(s));
        } catch (IOException | InternalBackendException e) {
            e.printStackTrace();
            return null;
        }
    }
}
