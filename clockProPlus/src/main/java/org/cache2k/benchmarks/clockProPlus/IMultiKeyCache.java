package org.cache2k.benchmarks.clockProPlus;

import java.util.List;

public interface IMultiKeyCache extends ISimpleCache {
    List<Boolean> request(List<String> groupedKeys);
}