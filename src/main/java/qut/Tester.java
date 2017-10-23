package qut;

import java.io.IOException;

public class Tester {

    public static void main(String[] args) {
        long time = System.nanoTime();
        try {
            Parallel.run("../referenceGenes.list", "../Ecoli");
        } catch (IOException e) {
            e.printStackTrace();
        }
        long parallelDiff = System.nanoTime() - time;
        System.out.println("Parallel run took " + (parallelDiff / 1000000000f) + " seconds to complete.");
        time = System.nanoTime();
        try {
            Sequential.run("../referenceGenes.list", "../Ecoli");
        } catch (IOException e) {
            e.printStackTrace();
        }
        long sequentialDiff = System.nanoTime() - time;
        System.out.println("Sequential run took " + (sequentialDiff / 1000000000f) + " seconds to complete.");

        System.out.println("Speedup is " + (sequentialDiff / (double) parallelDiff) + " with " + Parallel.getThreadCount() + " threads.");

        boolean same = true;

        for (String key : Parallel.consensus.keySet()) {
            Sigma70Consensus par = Parallel.consensus.get(key);
            Sigma70Consensus seq = Sequential.consensus.get(key);
            if (!par.toString().equals(seq.toString())) {
                same = false;
                break;
            }
        }

        if (!same) {
            System.out.println("The outputs are not the same between parallel and sequential!");
        }
    }
}
