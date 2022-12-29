package org.cache2k.benchmarks.clockProPlus;

import java.util.*;

/**
 * This code is part of EV-Store project
 * 
 * @author Daniar Kurniawan
 */

public class SimpleLFU implements ISimpleCache{
    HashMap<String, String> vals;// cache K and V
    HashMap<String, Integer> counts;// K and counters
    HashMap<Integer, LinkedHashSet<String>> lists;// Counter and item list
    int cap;
    public int size;
    int min = -1;

    public SimpleLFU(int capacity) {
        cap = capacity;
        this.size = capacity;
        vals = new HashMap<>();
        counts = new HashMap<>();
        lists = new HashMap<>();
        lists.put(1, new LinkedHashSet<>());
    }

    @Override
    public boolean request(String key) {
        if (get(key)) {
            return true;
        } else {
            set(key, key);
            return false;
        }
    }

    public boolean get(String key) {
        if (!vals.containsKey(key))
            return false;
        // Get the count from counts map
        int count = counts.get(key);
        // increase the counter
        counts.put(key, count + 1);
        // remove the element from the counter to linkedhashset
        lists.get(count).remove(key);

        // when current min does not have any data, next one would be the min
        if (count == min && lists.get(count).size() == 0)
            min++;
        if (!lists.containsKey(count + 1))
            lists.put(count + 1, new LinkedHashSet<>());
        lists.get(count + 1).add(key);
        // return vals.get(key);
        return true;
    }

    public void set(String key, String value) {
        if (cap <= 0)
            return;
        // If key does exist, we are returning from here
        if (vals.containsKey(key)) {
            vals.put(key, value);
            get(key);
            return;
        }
        if (vals.size() >= cap) {
            String evit = lists.get(min).iterator().next();
            lists.get(min).remove(evit);
            vals.remove(evit);
            counts.remove(evit);
        }
        // If the key is new, insert the value and current min should be 1 of course
        vals.put(key, value);
        counts.put(key, 1);
        min = 1;
        lists.get(1).add(key);
    }
    
    @Override
    public int getCacheSize() {
        return this.size;
    }
}
