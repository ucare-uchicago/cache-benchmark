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

import org.apache.commons.collections4.map.HashedMap;
import org.cache2k.benchmark.EvictionTuning;
import org.cache2k.benchmark.EvictionStatistics;

/**
 * Eviction algorithm based on the original CLOCK-Pro idea with simple adaption.
 *
 * <p>
 * This code was used for the experiments in the paper
 * <a href="https://doi.org/10.1145/3319647.3325838">Cong Li, 2019. CLOCK-Pro+:
 * Improving CLOCK-Pro Cache Replacement with Utility-Driven Adaptation</a>
 *
 * @author Cong Li 
 * Modified by: Daniar Kurniawan
 */
@SuppressWarnings({ "Duplicates", "WeakerAccess", "unused" })
public class ClockPro implements ISimpleCache {

    public ClockPro(int size, Tuning tuning) {
        this.size = size;
        this.clockMap = new HashedMap<>();
        this.coldTarget = (int) (this.size * tuning.getColdRatio());
        this.hotTarget = this.size - this.coldTarget;
        this.coldSize = 0;
        this.hotSize = 0;
        this.testSize = 0;
        this.handHot = null;
        this.handCold = null;
        this.handTest = null;
        this.lru = null;

        this.totalSize = 0;
        this.totalCount = 0;
        this.coldAccess = 0;
        this.coldHitTracked = 0;
        this.testExpire = 0;
        this.coldHit = 0;
        this.hotHit = 0;
    }

    @Override
    public boolean request(String address) {
        this.totalCount++;
        this.totalSize += this.hotTarget;

        if (this.clockMap.containsKey(address)) {
            CacheMetaData data = this.clockMap.get(address);
            if (data.isResident()) {
                data.setReference(true);
                if (data.isLIR()) {
                    this.hotHit++;
                } else {
                    this.coldHit++;
                }
                return true;
            }
        }
        this.advanceHandHot();
        this.advanceHandCold();
        if (this.clockMap.containsKey(address)) {
            CacheMetaData data = this.clockMap.get(address);
            this.assertStatement(data.isInStack(), "Error: hit a non-resident cold page out of the stack!");
            this.assertStatement(!data.isReferenced(), "Error: hit a referenced non-resident cold page");

            this.adjustColdTarget(1);

            this.assertStatement(this.hotSize + this.coldSize == this.size, "Error: cache not full!");
            this.evictColdPage();

            data.setResidentState(true);
            this.testSize--;
            this.coldSize++;

            if (data == this.handTest) {
                this.advanceHandTest();
            }

            this.promoteColdPage(data);
            this.advanceHandCold();

            return false;
        }

        if (this.coldSize + this.hotSize >= this.size) {
            if (this.handCold == null) {
                /**
                 * added by Daniar due to the bug catched when the program tries to use a null
                 * handCold
                 */
                // do nothing
            } else {
                this.evictColdPage();
            }
        }

        CacheMetaData data = new CacheMetaData();
        data.setAddress(address);
        data.setResidentState(true);
        this.addToClock(data);
        if (this.coldSize == 0 && this.hotSize < this.hotTarget) {
            data.setLIRState(true);
            this.hotSize++;
        } else {
            data.setLIRState(false);
            if (this.handCold == null) {
                this.handCold = data;
            }
            this.coldSize++;
        }
        this.advanceHandCold();
        this.pruneTestPages();
        return false;
    }

    public String getStatisticsString() {
        double avgHotRatio = this.totalSize / this.totalCount / this.size;
        String result = String.format(
                "avg. %f, hot hit %d, cold hit %d, cold hit tracked %d, cold access %d, test expire %d", avgHotRatio,
                this.hotHit, this.coldHit, this.coldHitTracked, this.coldAccess, this.testExpire);
        return result;
    }

    @Override
    public String toString() {
        StringBuffer str = new StringBuffer(String.format("ClockPro(%d, %s)", this.size, getStatisticsString()));
        str.append("\n    " + this.clockMap.keySet());
        return str.toString();
    }

    protected void pruneTestPages() {
        while (this.coldSize + this.hotSize + this.testSize > this.size * 2) {
            this.assertStatement(this.handTest.isInStack() && !this.handTest.isLIR() && !this.handTest.isResident(),
                    "Error: hand_test does not stop at a test page!");

            this.removeNonresidentColdPage(this.handTest);
            this.adjustColdTarget(-1);
        }
    }

    protected void evictColdPage() {
        this.assertStatement(!this.handCold.isLIR() && this.handCold.isResident() && !this.handCold.isReferenced(),
                "Error: hand_cold does not stop at a non-referenced resident cold page!");

        CacheMetaData data = this.handCold;
        this.handCold = this.handCold.getNext();
        data.setResidentState(false);
        this.coldSize--;
        this.testSize++;
        if (this.handTest == null) {
            this.handTest = data;
        }
        if (!data.isInStack()) {
            if (data == this.lru) {
                this.lru = data.getPrevious();
            }
            this.removeNonresidentColdPage(data);
        }
    }

    protected void promoteColdPage(CacheMetaData data) {
        data.setLIRState(true);
        this.hotSize++;
        this.coldSize--;

        this.moveToLRU(data);

        while (this.hotSize > this.hotTarget) {
            this.demoteHotPage();
        }
    }

    protected void demoteHotPage() {
        this.assertStatement(!this.handHot.isReferenced(), "Error: hand_hot stops on a referenced page!");

        CacheMetaData data = this.handHot;
        this.handHot = this.handHot.getNext();

        data.setLIRState(false);
        this.hotSize--;
        this.coldSize++;
        data.setInStackStatus(false);
        this.moveToLRU(data);

        this.advanceHandHot();
    }

    protected void advanceHandTest() {
        if (this.testSize == 0) {
            this.handTest = null;
            return;
        }
        while (this.handTest.isLIR() || this.handTest.isResident()) {
            this.handTest = this.handTest.getNext();
        }
    }

    protected void advanceHandHot() {
        if (this.hotSize == 0) {
            return;
        }
        CacheMetaData data = this.handHot;

        while (!data.isLIR() || data.isReferenced()) {
            CacheMetaData nextPosition = data.getNext();
            if (data.isLIR()) {
                data.setReference(false);
                this.lru = data;
            } else {
                if (data.isResident()) {
                    if (data.isReferenced()) {
                        this.coldHitTracked++;
                        this.adjustColdTarget(1);
                        data.setReference(false);
                        this.lru = data;
                        if (data.equals(this.handCold)) {
                            this.handCold = nextPosition;
                        }
                    } else {
                        data.setInStackStatus(false);
                        this.adjustColdTarget(-1);
                    }
                } else {
                    this.removeNonresidentColdPage(data);
                    this.adjustColdTarget(-1);
                }
                if (data.equals(this.handTest)) {
                    this.handTest = nextPosition;
                }
            }
            data = nextPosition;
        }
        this.handHot = data;
    }

    protected void advanceHandCold() {
        if (this.coldSize == 0) {
            return;
        }

        while (this.handCold.isLIR() || !this.handCold.isResident() || this.handCold.isReferenced()) {
            CacheMetaData data = this.handCold;
            this.handCold = this.handCold.getNext();
            if (!data.isLIR()) {
                if (data.isReferenced()) {
                    data.setReference(false);
                    if (data.isInStack()) {
                        this.coldHitTracked++;
                        this.adjustColdTarget(1);
                        this.promoteColdPage(data);
                    } else {
                        data.setInStackStatus(true);
                        this.moveToLRU(data);
                    }
                }
            }
        }
    }

    protected void addToClock(CacheMetaData data) {
        if (this.lru == null) {
            data.linkNext(data);
            this.lru = data;
            this.handHot = data;
        } else {
            data.insertAfter(this.lru);
            this.lru = data;
        }

        data.setInStackStatus(true);
        this.clockMap.put(data.getAddress(), data);
    }

    protected void moveToLRU(CacheMetaData data) {
        if (data == this.lru) {
            return;
        }

        data.unlink();
        data.insertAfter(this.lru);

        this.lru = data;
    }

    protected void removeNonresidentColdPage(CacheMetaData data) {
        String address = data.getAddress();
        if (data == this.handTest) {
            this.handTest = this.handTest.getNext();
        }
        this.clockMap.remove(address);
        data.unlink();
        this.testSize--;
        this.advanceHandTest();
    }

    protected void assertStatement(boolean result, String message) {
        if (!result) {
            System.err.println(message);
        }
    }

    protected void adjustColdTarget(int delta) {
        this.coldTarget += delta;
        if (delta == 1) {
            this.coldAccess++;
        } else {
            this.testExpire++;
        }
        if (this.coldTarget < 1) {
            this.coldTarget = 1;
        }
        if (this.coldTarget > this.size / 2) {
            this.coldTarget = this.size / 2;
        }
        this.hotTarget = this.size - this.coldTarget;
    }

    public void outputStatus() {
        if (this.lru != null) {
            this.lru.outputLinkedList();
        }
        System.out.printf("Hot_pages = %d, cold_pages = %d, hot_target = %d, cold_target = %d, ", this.hotSize,
                this.coldSize, this.hotTarget, this.coldTarget);
        if (this.handHot != null) {
            System.out.printf("Hot: %d, ", this.handHot.getAddress());
        }
        if (this.handCold != null) {
            System.out.printf("Cold: %d, ", this.handCold.getAddress());
        }
        if (this.handTest != null) {
            System.out.printf("Test: %d, ", this.handTest.getAddress());
        }
        System.out.println();
    }

    @Override
    public EvictionStatistics getEvictionStatistics() {
        return new EvictionStatistics() {
            @Override
            public double getAverageHotPercentage() {
                return totalSize * 100D / totalCount / size;
            }
        };
    }

    @Override
    public int getCacheSize() {
        return this.size;
    }

    protected HashedMap<String, CacheMetaData> clockMap;
    protected CacheMetaData handHot;
    protected CacheMetaData handCold;
    protected CacheMetaData handTest;
    protected CacheMetaData lru;
    protected int size;
    protected int coldSize;
    protected int hotSize;
    protected int testSize;
    protected int coldTarget;
    protected int hotTarget;

    protected int coldAccess;
    protected int coldHitTracked;
    protected int testExpire;

    protected int coldHit;
    protected int hotHit;

    protected double totalCount;
    protected double totalSize;

    @SuppressWarnings("unused")
    public static class Tuning implements EvictionTuning {

        private double coldRatio = 0.01;

        public Tuning() {
        }

        public Tuning(final double coldRatio) {
            this.coldRatio = coldRatio;
        }

        public double getColdRatio() {
            return coldRatio;
        }

        @Override
        public String toString() {
            return String.format("%.2f", coldRatio);
        }

    }

}
