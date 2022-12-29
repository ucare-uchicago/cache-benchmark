package org.cache2k.benchmarks.clockProPlus;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.map.LinkedMap;

/**
 * This code is part of EV-Store project
 * 
 * @author Daniar Kurniawan
 */

@SuppressWarnings({ "WeakerAccess", "unused" })
public class EvCAR implements IMultiKeyCache {

    protected int size;
    protected int decPartitionSize;
    protected int nGroup;
    protected Clock recency;
    protected Clock frequency;
    protected int recencyTarget;
    protected LinkedMap<String, CacheMetaData> recencyGhost;
    protected LinkedMap<String, CacheMetaData> frequencyGhost;

    public EvCAR(int size, int nGroup) {
        this.size = size;
        this.nGroup = nGroup;
        this.recency = new Clock(this.size);
        this.frequency = new Clock(this.size);
        this.recencyGhost = new LinkedMap<>();
        this.frequencyGhost = new LinkedMap<>();
        this.recencyTarget = 0;
        this.decPartitionSize = size / nGroup;
    }

    @Override
    public List<Boolean> request(List<String> groupedKeys) {
        List<Boolean> aggHitMissRecord = new ArrayList<Boolean>();
        int aggHit = 0;

        for (String address : groupedKeys) {
            if (this.recency.exists(address) || this.frequency.exists(address)) {
                aggHitMissRecord.add(true);
                aggHit++;
            } else {
                aggHitMissRecord.add(false);
            }
        }
        // Update the clocks
        for (String address : groupedKeys) {
            //System.out.println(aggHit);
            update(address, aggHit);
        }
        if (aggHit == aggHitMissRecord.size()) {
            // System.out.println("Perfect Hit ===== " + groupedKeys.toString());
        }
        return aggHitMissRecord;
    }

    boolean update(String address, int aggHit) {
        if (this.recency.exists(address)) {
            return this.recency.request(address);
        }
        if (this.frequency.exists(address)) {
            // System.out.println("-- Cond 2");
            return this.frequency.request(address, aggHit);
        }
        if (this.getCurrentCacheSize() == this.size) {
            // System.out.println("-- Cond 3");
            // System.out.println("     " + this.frequency.clock.keySet().toString());
            // System.out.println("getCurrentCacheSize() == this.size");
            this.replace(aggHit);
            // System.out.println("     " + this.frequency.clock.keySet().toString());
            if (!this.recencyGhost.containsKey(address) && !this.frequencyGhost.containsKey(address)) {
                // System.out.println("-- Cond 4 " + this.recency.getCurrentSize() + " + " + this.recencyGhost.size());
                if (this.recency.getCurrentSize() + this.recencyGhost.size() == this.size) {
                    // System.out.println("-- Cond 4  a   " );
                    String firstKey = this.recencyGhost.firstKey();
                    // System.out.println(" ghost_metadata_evict key " + firstKey);
                    // System.out.println(this.recencyGhost.keySet().toString());
                    this.recencyGhost.remove(firstKey);
                    // System.out.println("-- Cond 4  a  " + this.recency.getCurrentSize() + " + " + this.recencyGhost.size());
                } else if (this.getCurrentCacheSize() + this.recencyGhost.size()
                        + this.frequencyGhost.size() == this.size * 2) {
                    // System.out.println("-- Cond 4  b");
                    String firstKey = this.frequencyGhost.firstKey();
                    this.frequencyGhost.remove(firstKey);
                }
            }
        }
        if (!this.recencyGhost.containsKey(address) && !this.frequencyGhost.containsKey(address)) {
            // System.out.println("-- Cond 5 key "+ address);
            // System.out.println("     " + this.frequency.clock.keySet().toString());
            this.recency.insert(address, aggHit);
        } else if (this.recencyGhost.containsKey(address)) {
            // System.out.println("-- Cond 6");
            this.recencyTarget = Math.min(
                    this.recencyTarget + Math.max(1, this.frequencyGhost.size() / this.recencyGhost.size()), this.size);
            this.recencyGhost.remove(address);
            this.frequency.insert(address, aggHit);
        } else {
            // System.out.println("-- Cond 7  " + address);
            if (!this.frequencyGhost.containsKey(address)) {
                System.err.println("Error");
            }
            this.recencyTarget = Math
                    .max(this.recencyTarget - Math.max(1, this.recencyGhost.size() / this.frequencyGhost.size()), 0);
            this.frequencyGhost.remove(address);
            this.frequency.insert(address, aggHit);
        }
        return false;
    }

    @Override
    public boolean request(String address) {
        System.out.println("Should not reach here");
        System.exit(-1);
        return true;
    }

    @Override
    public String toString() {
        StringBuffer str = new StringBuffer(String.format("EvCAR(%d)", this.size));
        str.append(this.recency.clock.asList().toString() + " | ");
        str.append(this.frequency.clock.asList().toString());
        return str.toString();
    }

    protected int getCurrentCacheSize() {
        return this.recency.getCurrentSize() + this.frequency.getCurrentSize();
    }

    protected void replace(int aggHit) {
        int counter = 0;
        int decrementRate = 1;

        while (true) {
            // System.out.println("in while (cache_replace)");
            if (this.recency.getCurrentSize() >= Math.max(1, this.recencyTarget)) {
                CacheMetaData data = this.recency.check();
                this.recency.evict();
                if (data.isReferenced()) {
                    data.setReference(false);
                    // Move data to frequency CLOCK
                    this.frequency.insert(data.getAddress(), data.getAggHit());
                } else {
                    this.recencyGhost.put(data.getAddress(), data);
                    break;
                }
            } else {

                CacheMetaData data = this.frequency.check();
                // System.out.println("  while 2  " + data.address);
                if (data.isReferenced()) {
                    // System.out.println("  while 2  a" );
                    // The EvCar will decrease the aggHit by 1
                    if (data.getAggHit() <= aggHit) {
                        /* Only dereference/replace if the data.aggHit <= aggHit */
                        data.setReference(false);
                    } else {
                        /* only decrement if it will result into <= 0 */
                        if (data.aggHit - decrementRate <= 0) {
                            /* Progressive Decrement */
                            data.aggHit -= decrementRate;
                        }
                        counter++;
                        if (counter >= this.decPartitionSize) {
                            decrementRate++;
                            counter = 0;
                        }
                    }
                    this.frequency.move();
                } else {
                    // System.out.println("  while 2  b");
                    this.frequency.evict();
                    // System.out.println(" ghost_metadata_put == key " + data.address);
                    this.frequencyGhost.put(data.getAddress(), data);
                    break;
                }
            }
        }
        // System.out.println("done cache_replace");
        // System.exit(0);
    }
    
    @Override
    public int getCacheSize() {
        return this.size;
    }

}
