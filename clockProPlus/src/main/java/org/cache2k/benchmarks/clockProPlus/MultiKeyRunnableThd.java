package org.cache2k.benchmarks.clockProPlus;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * [Used by Ev-*] This runnable thread will send multiple key at a time
 */

/**
 * This code is part of EV-Store project
 * 
 * @author Daniar Kurniawan
 */

class MultiKeyRunnableThd extends SingleKeyRunnableThd {

    protected List<List<String>> groupedWorkloadKeys = new ArrayList<List<String>>();
    public IMultiKeyCache mca;

    MultiKeyRunnableThd(String name, ISimpleCache ca, List<String> workloadKeys,
            int nGroup) {
        // To satisfy the old code that doesn't run warm up
        super(name, ca, workloadKeys);
        groupTheWorkloadKeys(nGroup);
        this.mca = (IMultiKeyCache) ca;
    }

    void groupTheWorkloadKeys(int nGroup) {
        List<String> currGroupedKey;
        for (int i = 0; i < this.workloadKeys.size(); i = i + nGroup) {
            currGroupedKey = new ArrayList<String>();
            for (int j = i; j < nGroup + i; j++) {
                currGroupedKey.add(this.workloadKeys.get(j));
            }
            this.groupedWorkloadKeys.add(currGroupedKey);
        }
        System.out.println("Done grouping the BENCHMARK keys (" + groupedWorkloadKeys.size() + " groups). nGroup = " + nGroup);
    }

    // WARM-up the cache! 100% workload
    @Override
    public void warmUpTheCache(int warmUpMultiplier) {
        int idx = 0;
        System.out.println("Running Warm-UP 100% workload sequancially");
        // run sequentially, no randomize
        System.out.println("   run sequentially, no randomize");
        while (idx < this.groupedWorkloadKeys.size()) {
            // if (idx % 1000 == 0) {
            //     System.out.println("   req idx =" + idx);
            // }
            this.mca.request(this.groupedWorkloadKeys.get(idx));
            idx++;
        }
        System.out.println("Running Warm-UP is DONE!");
    }
    
    // @Override
    public void warmUpTheCacheRandomly(int warmUpMultiplier) {
        // will run 3x cachesize to fill up the empty cache queue
        int warmUpSize = this.mca.getCacheSize() * warmUpMultiplier;
        int max = this.groupedWorkloadKeys.size() - 1;
        int idx = 0;
        Random rand = new Random(warmUpSize); // seed = warmUpSize
        System.out.println("Running Warm-UP " + warmUpMultiplier + "x cachesize");
        System.out.println("    warmUpSize = " + warmUpSize +
            "  ; this.groupedWorkloadKeys.size = " + (this.groupedWorkloadKeys.size()*26));
        if (warmUpSize >= (this.groupedWorkloadKeys.size() * 26)) {
            // run sequentially, no randomize
            System.out.println("   run sequentially, no randomize");
            while (warmUpSize >= 0 && idx < this.groupedWorkloadKeys.size()) {
                this.mca.request(this.groupedWorkloadKeys.get(idx));
                idx++;
                warmUpSize -= 26;
            }
        } else {
            // pick random id for warm-up 
            while (warmUpSize >= 0) {
                idx = rand.nextInt(max);
                this.mca.request(this.groupedWorkloadKeys.get(idx));
                warmUpSize -= 26;
            }
        }
        // System.out.println("Running Warm-UP on EV-Star is CANCELLED, the aggHit value will be bias since we intentionally create a repeating workload pattern !");
        System.out.println("Running Warm-UP is DONE!");
    }

    @Override
    public void run() {
        System.out.println("Running MultiKeyRunnableThd " + threadName);
        // int counter = 0;
        for (List<String> groupedKeys : this.groupedWorkloadKeys) {
            List<Boolean> aggHitMissRecord = this.mca.request(groupedKeys);
            /**
             * Enable printing below for debugging per cache request
             */
            // if (counter >= 4400)
            // System.out.println( groupedKeys.toString() + " " + mca.toString());
            for (Boolean isHit : aggHitMissRecord) {
                this.recordHitMiss.add(isHit);
            }
            // counter += 1;
            // if (counter == 1000000) {
            // break;
            // }
        }
        System.out.println("Thread " + this.threadName + " exiting.");
    }
}
