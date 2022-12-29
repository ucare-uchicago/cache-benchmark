package org.cache2k.benchmarks.clockProPlus;

/*
 * #%L
 * Benchmarks: Clock-Pro+ and other eviction policies
 * %%
 * Copyright (C) 2018 - 2019 Cong Li, Intel Corporation
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.commons.collections4.map.LinkedMap;

@SuppressWarnings("WeakerAccess")
public class SimpleLRU implements ISimpleCache {

    public SimpleLRU(int size) {
        this.size = size;
        this.lru = new LinkedMap<String, CacheMetaData>();
    }

    @Override
    public synchronized boolean request(String address) {
        if (!this.lru.containsKey(address)) {
            if (this.lru.size() >= this.size) {
                String firstKey = this.lru.firstKey();
                this.lru.remove(firstKey);
            }
            this.lru.put(address, new CacheMetaData());
            return false;
        } else {
            CacheMetaData data = this.lru.get(address);
            this.lru.remove(address);
            this.lru.put(address, data);
            return true;
        }
    }

    @Override
    public String toString() {
        StringBuffer str = new StringBuffer(String.format("LRU(%d)", this.size));
        str.append(this.lru.asList().toString());
        return str.toString();
    }

    @Override
    public int getCacheSize() {
        return this.size;
    }

    protected int size;
    protected LinkedMap<String, CacheMetaData> lru;

}
