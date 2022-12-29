package org.cache2k.benchmarks.clockProPlus;

import org.apache.commons.collections4.map.LinkedMap;

/**
 * @author Jens Wilke
 */
@SuppressWarnings("WeakerAccess")
public class SimpleFIFO implements ISimpleCache {

    public SimpleFIFO(int size) {
        this.size = size;
        this.fifoList = new LinkedMap<String, CacheMetaData>();
    }

    @Override
    public boolean request(String address) {
        if (!this.fifoList.containsKey(address)) {
            if (this.fifoList.size() >= this.size) {
                String firstKey = this.fifoList.firstKey();
                this.fifoList.remove(firstKey);
            }
            this.fifoList.put(address, new CacheMetaData());
            return false;
        } else {
            return true;
        }
    }

    @Override
    public String toString() {
        StringBuffer str = new StringBuffer(String.format("FIFO(%d)", this.size));
        str.append(this.fifoList.asList().toString());
        return str.toString();
    }

    @Override
    public int getCacheSize() {
        return this.size;
    }

    protected int size;
    protected LinkedMap<String, CacheMetaData> fifoList;

}
