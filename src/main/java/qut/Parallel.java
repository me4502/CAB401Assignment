package qut;

import edu.au.jacobi.pattern.Match;
import edu.au.jacobi.pattern.Series;
import jaligner.BLOSUM62;
import jaligner.Sequence;
import jaligner.SmithWatermanGotoh;
import jaligner.matrix.Matrix;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class Parallel {
    public static HashMap<String, Sigma70Consensus> consensus = new HashMap<>();
    // Sigma70 pattern is stateful, keep it in a ThreadLocal.
    private static ThreadLocal<Series> sigma70_pattern = ThreadLocal.withInitial(
            () -> Sigma70Definition.getSeriesAll_Unanchored(0.7));
    private static final Matrix BLOSUM_62 = BLOSUM62.Load();
    private static byte[] complement = new byte['z'];

    static {
        complement['C'] = 'G';
        complement['c'] = 'g';
        complement['G'] = 'C';
        complement['g'] = 'c';
        complement['T'] = 'A';
        complement['t'] = 'a';
        complement['A'] = 'T';
        complement['a'] = 't';
    }


    private static List<Gene> ParseReferenceGenes(String referenceFile) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(referenceFile)));
        List<Gene> referenceGenes = new ArrayList<>();
        while (true) {
            String name = reader.readLine();
            if (name == null) {
                break;
            }
            String sequence = reader.readLine();
            referenceGenes.add(new Gene(name, 0, 0, sequence));
            consensus.put(name, new Sigma70Consensus());
        }
        consensus.put("all", new Sigma70Consensus());
        reader.close();
        return referenceGenes;
    }

    private static boolean Homologous(PeptideSequence A, PeptideSequence B) {
        return SmithWatermanGotoh.align(new Sequence(A.toString()), new Sequence(B.toString()), BLOSUM_62, 10f, 0.5f).calculateScore() >= 60;
    }

    private static NucleotideSequence GetUpstreamRegion(NucleotideSequence dna, Gene gene) {
        int upStreamDistance = 250;
        if (gene.location < upStreamDistance) {
            upStreamDistance = gene.location - 1;
        }

        if (gene.strand == 1) {
            return new NucleotideSequence(java.util.Arrays.copyOfRange(dna.bytes, gene.location - upStreamDistance - 1, gene.location - 1));
        } else {
            byte[] result = new byte[upStreamDistance];
            int reverseStart = dna.bytes.length - gene.location + upStreamDistance;
            for (int i = 0; i < upStreamDistance; i++) {
                result[i] = complement[dna.bytes[reverseStart - i]];
            }
            return new NucleotideSequence(result);
        }
    }

    private static Match PredictPromoter(NucleotideSequence upStreamRegion) {
        return BioPatterns.getBestMatch(sigma70_pattern.get(), upStreamRegion.toString());
    }

    private static void ProcessDir(List<String> list, File dir) {
        if (dir.exists()) {
            for (File file : dir.listFiles()) {
                if (file.isDirectory()) {
                    ProcessDir(list, file);
                } else {
                    list.add(file.getPath());
                }
            }
        }
    }

    private static List<String> ListGenbankFiles(String dir) {
        List<String> list = new ArrayList<>();
        ProcessDir(list, new File(dir));
        return list;
    }

    private static GenbankRecord Parse(String file) throws IOException {
        GenbankRecord record = new GenbankRecord();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        record.Parse(reader);
        reader.close();
        return record;
    }

    public static int getThreadCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    // Create a fixed thread pool with the number of threads the CPU is capable of.
    private static ExecutorService executorService = Executors.newFixedThreadPool(getThreadCount());

    public static void run(String referenceFile, String dir) throws IOException {
        System.out.println("Using a thread pool with " + getThreadCount() + " worker threads!");
        List<Callable<Void>> callableList = new ArrayList<>();

        List<Gene> referenceGenes = ParseReferenceGenes(referenceFile);
        for (String filename : ListGenbankFiles(dir)) {
            System.out.println("Scanning genbank file: " + filename);
            GenbankRecord record = Parse(filename);
            for (Gene referenceGene : referenceGenes) {
                System.out.println("Queuing up tasks for reference gene: " + referenceGene.name);
                for (Gene gene : record.genes) {
                    callableList.add(new GeneCallable(gene, referenceGene, record));
                }
            }
        }

        System.out.println("Running scheduled gene matches.");

        try {
            executorService.invokeAll(callableList);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (Map.Entry<String, Sigma70Consensus> entry : consensus.entrySet()) {
            System.out.println(entry.getKey() + " " + entry.getValue());
        }

        executorService.shutdown();
    }

    public static void main(String[] args) throws IOException {
        System.in.read(); // Wait for some input before continuing. The profiler I'm using automatically uses sampling, so I have to disable it and
        // re-enable it. Waiting means I catch everything that's useful in the profiling.
        long time = System.nanoTime();
        run("../referenceGenes.list", "../Ecoli");
        long diff = System.nanoTime() - time;
        System.out.println("Took " + (diff / 1000000000f) + " seconds to complete.");
    }

    //
    private static final ReentrantLock lock = new ReentrantLock();

    private static class GeneCallable implements Callable<Void> {

        private Gene gene;
        private Gene referenceGene;
        private GenbankRecord record;

        public GeneCallable(Gene gene, Gene referenceGene, GenbankRecord record) {
            this.gene = gene;
            this.referenceGene = referenceGene;
            this.record = record;
        }

        @Override
        public Void call() throws Exception {
            if (Homologous(gene.sequence, referenceGene.sequence)) {
                NucleotideSequence upStreamRegion = GetUpstreamRegion(record.nucleotides, gene);
                Match prediction = PredictPromoter(upStreamRegion);
                if (prediction != null) {
                    lock.lock();
                    consensus.get(referenceGene.name).addMatch(prediction);
                    consensus.get("all").addMatch(prediction);
                    lock.unlock();
                }
            }

            return null;
        }
    }
}
