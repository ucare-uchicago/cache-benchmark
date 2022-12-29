package org.cache2k.benchmarks.clockProPlus;

import org.apache.commons.collections4.map.LinkedMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * This code is part of EV-Store project
 * 
 * @author Daniar Kurniawan
 */

public class EvARC implements IMultiKeyCache {

    protected int size;
    protected int nGroup;
    protected LinkedMap<String, CacheMetaData> recencyList;
    protected LinkedMap<String, CacheMetaData> frequencyList;
    HashMap<String, Integer> keyMapAggHit;// K and counters
    HashMap<Integer, LinkedHashSet<String>> itemGroupByCounter;// Counter and item list
    int min = 0;

    protected int recencyTarget;
    protected LinkedMap<String, CacheMetaData> recencyGhost; // ghost (shadow) list
    protected LinkedMap<String, CacheMetaData> frequencyGhost; // ghost (shadow) list

    /*
     * Add flushing:
     */
    int nPerfectItem = 0;
    double flushRate = 0.1;
    double perfectItemCapacity = 0.95;

    public EvARC(int size, int nGroup) {
        this.size = size;
        this.recencyTarget = 0;
        this.nGroup = nGroup;
        this.recencyList = new LinkedMap<>();
        this.frequencyList = new LinkedMap<>();
        this.recencyGhost = new LinkedMap<>();
        this.frequencyGhost = new LinkedMap<>();
        keyMapAggHit = new HashMap<>();
        itemGroupByCounter = new HashMap<>();
        itemGroupByCounter.put(0, new LinkedHashSet<>());
    }

    @Override
    public List<Boolean> request(List<String> groupedKeys) {
        List<Boolean> aggHitMissRecord = new ArrayList<Boolean>();
        int aggHit = 0;

        for (String address : groupedKeys) {
            if (this.recencyList.containsKey(address) || this.frequencyList.containsKey(address)) {
                aggHitMissRecord.add(true);
                aggHit++;
            } else {
                aggHitMissRecord.add(false);
            }
        }
        for (String address : groupedKeys) {

            update(address, aggHit);
            // System.out.println(getCurrentCacheSize());
        }
        if (itemGroupByCounter.containsKey(nGroup) && !itemGroupByCounter.get(nGroup).isEmpty()) {
            nPerfectItem = itemGroupByCounter.get(nGroup).size();
            // System.out.println(nPerfectItem);
        }
        return aggHitMissRecord;
    }

    boolean update(String address, int aggHit) {
        // A HIT
        if (this.frequencyList.containsKey(address)) {
            get(address, aggHit);
            return true;
        }
        if (this.recencyList.containsKey(address)) {
            CacheMetaData data = this.recencyList.get(address);
            this.recencyList.remove(address);
            putPageInFreqList(address, data, aggHit);
            this.printSize();
            return true;
        }
        // A MISS
        if (this.recencyGhost.containsKey(address)) {
            int delta = 0;
            if (this.recencyGhost.size() >= this.frequencyGhost.size()) {
                delta = 1;
            } else {
                delta = this.frequencyGhost.size() / this.recencyGhost.size();
            }
            this.recencyTarget += delta;
            if (this.recencyTarget > this.size) {
                this.recencyTarget = this.size;
            }
            this.replace(address, aggHit);
            CacheMetaData data = this.recencyGhost.get(address);
            this.recencyGhost.remove(address);
            putPageInFreqList(address, data, aggHit);
            this.printSize();
            return false;
        }
        if (this.frequencyGhost.containsKey(address)) {
            int delta;
            if (this.frequencyGhost.size() >= this.recencyGhost.size()) {
                delta = 1;
            } else {
                delta = this.recencyGhost.size() / this.frequencyGhost.size();
            }
            this.recencyTarget -= delta;
            if (this.recencyTarget < 0) {
                this.recencyTarget = 0;
            }
            this.replace(address, aggHit);
            CacheMetaData data = this.frequencyGhost.get(address);
            this.frequencyGhost.remove(address);
            putPageInFreqList(address, data, aggHit);
            this.printSize();
            return false;
        }
        if (this.recencyList.size() + this.recencyGhost.size() == this.size) {
            if (this.recencyList.size() < this.size) {
                String firstKey = this.recencyGhost.firstKey();
                this.recencyGhost.remove(firstKey);
                this.replace(address, aggHit);//
            } else {
                String firstKey = this.recencyList.firstKey();
                this.recencyList.remove(firstKey);
            }
        } else {
            if (this.recencyList.size() + this.recencyGhost.size() + this.frequencyList.size()
                    + this.frequencyGhost.size() >= this.size) {
                if (this.recencyList.size() + this.recencyGhost.size() + this.frequencyList.size()
                        + this.frequencyGhost.size() == 2
                                * this.size) {
                    String firstKey = this.frequencyGhost.firstKey();
                    this.frequencyGhost.remove(firstKey);
                }
                this.replace(address, aggHit);
            }
        }
        CacheMetaData data = new CacheMetaData();
        this.recencyList.put(address, data);
        this.printSize();
        return false;
    }

    @Override
    public boolean request(String address) {
        System.out.println("Should not reach here");
        System.exit(-1);
        return false;
    }

    protected void printSize() {
        if (this.recencyList.size() + this.frequencyList.size() > this.size) {
            System.err.printf("%d, %d, %d, %d, %d\n", this.recencyList.size(), this.frequencyList.size(),
                    this.recencyGhost.size(),
                    this.frequencyGhost.size(), this.recencyTarget);
        }
    }

    protected int getCurrentCacheSize() {
        return this.recencyList.size() + this.frequencyList.size();
    }

    public boolean get(String key, int aggHit) {
        if (!frequencyList.containsKey(key))
            return false;
        // Get the count from counts map and update the counter:
        int count = keyMapAggHit.get(key);
        int newCount = count;
        // increase the counter(different from LFU)
        if (count < aggHit) {
            newCount = aggHit;
        }
        keyMapAggHit.put(key, newCount);
        // remove the element from the counter to linkedhashset
        itemGroupByCounter.get(count).remove(key);
        if (!itemGroupByCounter.containsKey(newCount))
            itemGroupByCounter.put(newCount, new LinkedHashSet<>());
        itemGroupByCounter.get(newCount).add(key);
        // when current min does not have any data, next one would be the min
        if (count == min) {
            while (!itemGroupByCounter.containsKey(min) || itemGroupByCounter.get(min).size() == 0) {
                min++;
            }
        }
        this.printSize();
        return true;
    }

    public void putPageInFreqList(String key, CacheMetaData value, int aggHit) {
        /*
         * Flushing:
         */
        if (nPerfectItem >= (int) (this.size * perfectItemCapacity)) {
            int historyLRUSize = this.recencyList.size();
            for (int i = 0; i < (int) (flushRate * this.size); i++) {
                String keyToEvict = itemGroupByCounter.get(nGroup).iterator().next();
                CacheMetaData data = this.frequencyList.get(keyToEvict);
                itemGroupByCounter.get(nGroup).remove(keyToEvict);
                frequencyList.remove(keyToEvict);
                keyMapAggHit.remove(keyToEvict);
                // flush the cache page in the TOPLRU2 to TOPLRU1:
                this.recencyList.put(keyToEvict, data);
                // flush the cache page from Bottom1 to Bottom2:
                if (!this.recencyGhost.isEmpty()) {
                    String firstKey = this.recencyGhost.firstKey();
                    CacheMetaData data2 = this.recencyGhost.get(firstKey);
                    this.recencyGhost.remove(firstKey);
                    this.frequencyGhost.put(firstKey, data2);
                }
            }
            for (int j = 0; j < this.recencyList.size() - historyLRUSize; j++) {
                String firstKey = this.recencyList.firstKey();
                CacheMetaData tmpData = this.recencyList.get(firstKey);
                this.recencyList.remove(firstKey);
                this.recencyList.put(firstKey, tmpData);
            }
            nPerfectItem = itemGroupByCounter.get(nGroup).size();
            // cache opens up to new keys though low aggHit
            if (frequencyList.size() < getCurrentCacheSize()) {
                min = aggHit;
            }
            this.recencyTarget += (int) (flushRate * this.size);
        }

        if (aggHit >= min) {
            frequencyList.put(key, value);
            keyMapAggHit.put(key, aggHit);

            if (!itemGroupByCounter.containsKey(aggHit))
                itemGroupByCounter.put(aggHit, new LinkedHashSet<>());
            itemGroupByCounter.get(aggHit).add(key);
        } else {
            min = aggHit;
            // if(topLRU2.size() < size - recencyTarget - 1){
            frequencyList.put(key, value);
            keyMapAggHit.put(key, aggHit);

            if (!itemGroupByCounter.containsKey(aggHit))
                itemGroupByCounter.put(aggHit, new LinkedHashSet<>());
            itemGroupByCounter.get(aggHit).add(key);
        }
        // current min should be recalculated
        while (!itemGroupByCounter.containsKey(min) || itemGroupByCounter.get(min).size() == 0) {
            min++;
        }
    }

    protected void replace(String key, int aggHit) {
        if (!this.recencyList.isEmpty() && (this.recencyList.size() > this.recencyTarget
                || (this.frequencyGhost.containsKey(key) && this.recencyList.size() == this.recencyTarget))) {
            String firstKey = this.recencyList.firstKey();
            CacheMetaData tmpData = this.recencyList.get(firstKey);
            this.recencyList.remove(firstKey);
            this.recencyGhost.put(firstKey, tmpData);
        } else {
            while (!itemGroupByCounter.containsKey(min) || itemGroupByCounter.get(min).size() == 0) {
                min++;
            }
            String evit = itemGroupByCounter.get(min).iterator().next();
            CacheMetaData tmpData = this.frequencyList.get(evit);
            itemGroupByCounter.get(min).remove(evit);
            frequencyList.remove(evit);
            keyMapAggHit.remove(evit);
            this.frequencyGhost.put(evit, tmpData);

        }
    }

    @Override
    public int getCacheSize() {
        return this.size;
    }

}