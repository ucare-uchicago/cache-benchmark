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

/**
 * Eviction algorithm based on the original CAR idea.
 *
 * <p>
 * This code was used for the experiments in the paper
 * <a href="https://doi.org/10.1145/3319647.3325838">Cong Li, 2019. CLOCK-Pro+:
 * Improving CLOCK-Pro Cache Replacement with Utility-Driven Adaptation</a>
 *
 * @author Cong Li 
 * Modified by: Daniar Kurniawan
 */
@SuppressWarnings({ "WeakerAccess", "unused" })
public class CAR implements ISimpleCache {

    public CAR(int size) {
        this.size = size;
        this.recency = new Clock(this.size);
        this.frequency = new Clock(this.size);
        this.recencyGhost = new LinkedMap<>();
        this.frequencyGhost = new LinkedMap<>();
        this.recencyTarget = 0;
    }

    @Override
    public boolean request(String address) {
        if (this.recency.exists(address)) {
            return this.recency.request(address);
        }
        if (this.frequency.exists(address)) {
            return this.frequency.request(address);
        }
        if (this.getCurrentCacheSize() == this.size) {
            this.replace();
            if (!this.recencyGhost.containsKey(address) && !this.frequencyGhost.containsKey(address)) {
                if (this.recency.getCurrentSize() + this.recencyGhost.size() == this.size) {
                    String firstKey = this.recencyGhost.firstKey();
                    this.recencyGhost.remove(firstKey);
                } else if (this.getCurrentCacheSize() + this.recencyGhost.size()
                        + this.frequencyGhost.size() == this.size * 2) {
                    String firstKey = this.frequencyGhost.firstKey();
                    this.frequencyGhost.remove(firstKey);
                }
            }
        }
        if (!this.recencyGhost.containsKey(address) && !this.frequencyGhost.containsKey(address)) {
            this.recency.insert(address);
        } else if (this.recencyGhost.containsKey(address)) {
            this.recencyTarget = Math.min(
                    this.recencyTarget + Math.max(1, this.frequencyGhost.size() / this.recencyGhost.size()), this.size);
            this.recencyGhost.remove(address);
            this.frequency.insert(address);
        } else {
            if (!this.frequencyGhost.containsKey(address)) {
                System.err.println("Error");
            }
            this.recencyTarget = Math
                    .max(this.recencyTarget - Math.max(1, this.recencyGhost.size() / this.frequencyGhost.size()), 0);
            this.frequencyGhost.remove(address);
            this.frequency.insert(address);
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuffer str = new StringBuffer(String.format("CAR(%d)", this.size));
        str.append(this.recency.clock.asList().toString());
        str.append(this.frequency.clock.asList().toString());
        return str.toString();
    }

    protected int getCurrentCacheSize() {
        return this.recency.getCurrentSize() + this.frequency.getCurrentSize();
    }

    protected void replace() {
        while (true) {
            if (this.recency.getCurrentSize() >= Math.max(1, this.recencyTarget)) {
                CacheMetaData data = this.recency.check();
                if (data.isReferenced()) {
                    data.setReference(false);
                    this.recency.evict();
                    this.frequency.insert(data.getAddress());
                } else {
                    this.recency.evict();
                    this.recencyGhost.put(data.getAddress(), data);
                    break;
                }
            } else {
                CacheMetaData data = this.frequency.check();
                if (data.isReferenced()) {
                    data.setReference(false);
                    this.frequency.move();
                } else {
                    this.frequency.evict();
                    this.frequencyGhost.put(data.getAddress(), data);
                    break;
                }
            }
        }
    }
    
    @Override
    public int getCacheSize() {
        return this.size;
    }

    protected int size;
    protected Clock recency;
    protected Clock frequency;
    protected int recencyTarget;
    protected LinkedMap<String, CacheMetaData> recencyGhost;
    protected LinkedMap<String, CacheMetaData> frequencyGhost;

}
