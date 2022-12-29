package org.cache2k.benchmarks.clockProPlus;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * This runnable thread will send one key at a time
 */

/**
 * This code is part of EV-Store project
 * 
 * @author Daniar Kurniawan
 */

class SingleKeyRunnableThd implements Runnable {
    public Thread t;
    public ISimpleCache ca;
    protected ArrayList<Boolean> recordHitMiss;
    protected String threadName;
    protected List<String> workloadKeys;
    protected int localHitCounter;

    protected static int N_EV_TABLE = 26;

    SingleKeyRunnableThd(String name, ISimpleCache ca, List<String> workloadKeys) {
        // To satisfy the old code that doesn't run warm up
        this.threadName = name;
        this.workloadKeys = workloadKeys;
        this.ca = ca;
        this.localHitCounter = 0;
        System.out.println("Creating " + this.threadName);
        this.recordHitMiss = new ArrayList<Boolean>();
    }
    
    public int getLocalHitCounter() {
        return this.localHitCounter;
    }

    public ArrayList<Boolean> getRecordHitMiss() {
        return this.recordHitMiss;
    }

    // WARM-up the cache! 100% workload
    void warmUpTheCache(int warmUpMultiplier) {
        int idx = 0;
        System.out.println("Running Warm-UP 100% workload sequancially");
        // run sequentially, no randomize
        System.out.println("   run sequentially, no randomize");
        while (idx < this.workloadKeys.size()) {
            this.ca.request(this.workloadKeys.get(idx));
            idx++;
        }
        System.out.println("Running Warm-UP is DONE!");
    }

    // WARM-up the cache! 3x cache-size
    void warmUpTheCacheRandomly(int warmUpMultiplier) {
        // will run 3x cachesize to fill up the empty cache queue
        // The multiplier is multiplied to the number of N_EV_TABLE because 1 workload is 1 key; 
        //    while in the MultiKeyRunnable.., 1 workload is 26 keys!!
        int warmUpSize = this.ca.getCacheSize() * warmUpMultiplier;
        int max = this.workloadKeys.size()-1;
        int idx = 0;
        Random rand = new Random(warmUpSize); // seed = warmUpSize
        System.out.println("Running Warm-UP " + warmUpMultiplier + "x cachesize");
        System.out.println("    warmUpSize = " + warmUpSize + "  ; this.workloadKeys.size = " + this.workloadKeys.size());

        if (warmUpSize >= this.workloadKeys.size()) {
            // run sequentially, no randomize
            System.out.println("   run sequentially, no randomize");
            while (idx < this.workloadKeys.size()) {
                this.ca.request(this.workloadKeys.get(idx));
                idx++;
            }
        } else {
            // pick random id for warm-up 
            while (warmUpSize >= 0) {
                idx = rand.nextInt(max);
                this.ca.request(this.workloadKeys.get(idx));
                warmUpSize -= 1;
            }
        }
        System.out.println("Running Warm-UP is DONE!");
    }
    
    public void run() {
        // int count = 0;
        for (String key : this.workloadKeys) {
            // count++;
            if (this.ca.request(key)) {
                this.localHitCounter++;
                this.recordHitMiss.add(true);
            } else {
                this.recordHitMiss.add(false);
            }
            // System.out.println(key + " :" + ca.toString());
        }
        System.out.println("Thread " + this.threadName + " exiting.");
    }

    public void start() {
        System.out.println("Starting " + this.threadName);
        if (t == null) {
            t = new Thread(this, this.threadName);
            t.start(); // will execute void run()
        }
    }
}
