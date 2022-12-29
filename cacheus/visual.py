from algs.lib.cacheop import CacheOp
from algs.lib.traces import identify_trace, get_trace_reader
from algs.lib.progress_bar import ProgressBar
from algs.lib.visualizinator import Visualizinator
from algs.get_algorithm import get_algorithm
from timeit import timeit
from itertools import product
import numpy as np
import csv
import os

import matplotlib.pyplot as plt

progress_bar_size = 30


class AlgorithmTest:
    def __init__(self, algorithm, cache_size, trace_file, alg_args, **kwargs):
        self.algorithm = algorithm
        self.cache_size = cache_size
        self.trace_file = trace_file
        self.alg_args = alg_args

        trace_type = identify_trace(trace_file)
        trace_reader = get_trace_reader(trace_type)
        self.reader = trace_reader(trace_file, **kwargs)

        self.misses = 0
        self.filters = 0
        self.writes = 0

    def run(self, requests):
        alg = get_algorithm(self.algorithm)(self.cache_size, round(0.1*requests), **self.alg_args)
        progress_bar = ProgressBar(progress_bar_size,
                                   title="{} {}".format(
                                       self.algorithm, self.cache_size))

        for lba, write, ts in self.reader.read():
            op, evicted = alg.request(lba, ts)
            if op == CacheOp.INSERT:
                self.misses += 1
                self.writes += 1
            elif op == CacheOp.HIT and write:
                self.writes += 1
            elif op == CacheOp.FILTER:
                self.misses += 1
                self.filters += 1
            progress_bar.progress = self.reader.progress
            progress_bar.print()
        progress_bar.print_complete()
        self.avg_pollution = np.mean(
            alg.pollution.Y
        ) if 'enable_pollution' in self.alg_args and self.alg_args[
            'enable_pollution'] else None

        alg.visual.tracked_values["pollution"] = list(
            zip(alg.pollution.X, alg.pollution.Y))

        ios = self.reader.num_requests()
        hits = ios - self.misses
        writes = self.writes
        filters = self.filters
        print(
            "Results: {:<10} size={:<8} hits={}, misses={}, hitrate={:4}% writes={} filters={} {}"
            .format(self.algorithm, self.cache_size, hits, self.misses,
                    round(100 * hits / ios, 2), writes, filters,
                    self.trace_file),
            *(self.alg_args.items() if self.alg_args else {}))

        return alg.visual


def runEntireTrace(trace_name, kwargs, title=None):
    trace_type = identify_trace(trace_name)
    trace_reader = get_trace_reader(trace_type)
    reader = trace_reader(trace_name, **kwargs)

    progress_bar = ProgressBar(progress_bar_size, title=title)

    for lba in reader.read():
        progress_bar.progress = reader.progress
        progress_bar.print()
    progress_bar.print_complete()

    return reader


def getUniqueCount(trace_name, kwargs):
    reader = runEntireTrace(trace_name, kwargs, title="Counting Uniq")
    return reader.num_unique(), reader.num_requests()


def getReuseCount(trace_name, kwargs):
    reader = runEntireTrace(trace_name, kwargs, title="Counting Reuse")
    return reader.num_reuse(), reader.num_requests()


def generateTraceNames(trace):
    if trace.startswith('~'):
        trace = os.path.expanduser(trace)

    if os.path.isdir(trace):
        for trace_name in os.listdir(trace):
            yield os.path.join(trace, trace_name)
    elif os.path.isfile(trace):
        yield trace
    else:
        raise ValueError("{} is not a directory or a file".format(trace))


def generateAlgorithmTests(algorithm, cache_size, trace_name, config):
    if algorithm in config:
        alg_config = config[algorithm]
        alg_config['enable_visual'] = True
        alg_config['enable_pollution'] = True
        yield AlgorithmTest(algorithm, cache_size, trace_name, alg_config,
                            **config)
    else:
        alg_config = {'enable_visual': True, 'enable_pollution': True}
        yield AlgorithmTest(algorithm, cache_size, trace_name, alg_config,
                            **config)


if __name__ == '__main__':
    import sys
    import json
    import math
    import os

    with open(sys.argv[1], 'r') as f:
        config = json.loads(f.read())

    # TODO revisit and cleanup
    if 'request_count_type' in config:
        if config['request_count_type'] == 'reuse':
            requestCounter = getReuseCount
        elif config['request_count_type'] == 'unique':
            requestCounter = getUniqueCount
        else:
            raise ValueError("Unknown request_count_type found in config")
    else:
        requestCounter = getUniqueCount

    for trace in config['traces']:
        for trace_name in generateTraceNames(trace):
            if any(map(lambda x: isinstance(x, float), config['cache_sizes'])):
                count, requestTotal = requestCounter(trace_name, config)
            else:
                _, requestTotal = requestCounter(trace_name, config)
            for cache_size in config['cache_sizes']:
                tmp_cache_size = cache_size
                if isinstance(cache_size, float):
                    cache_size = math.floor(cache_size * count)
                if cache_size < 10:
                    print(
                        "Cache size {} too small for trace {}. Calculated size is {}. Skipping"
                        .format(tmp_cache_size, trace_name, cache_size),
                        file=sys.stderr)
                    continue

                # visualizinator for all tests
                visuals = Visualizinator(labels=[])

                for algorithm in config['algorithms']:
                    for test in generateAlgorithmTests(algorithm, cache_size,
                                                       trace_name, config):
                        alg_visual = test.run(requestTotal)

                        for label, vals in alg_visual.tracked_values.items():
                            visuals.tracked_values["{} {}".format(
                                algorithm, label)] = vals

                for name, specs in config['graphs'].items():
                    subplot_count = len(specs)
                    subplots = [
                        plt.subplot(subplot_count, 1, x + 1)
                        for x in range(subplot_count)
                    ]

                    for subplot, spec in zip(subplots, specs):
                        visuals.visualize(subplot, **spec)

                    plt.tight_layout()
                    plt.subplots_adjust(hspace=0.1)

                    if 'output_dir' in config:
                        # TODO figure out how best to include config here
                        #      IDEA: have a converter from config to string
                        #            would help with csv too?
                        sane_trace_name = trace_name.split('/')[-1]
                        plt.savefig("{}/{}_{}_{}.png".format(
                            config['output_dir'], sane_trace_name, cache_size,
                            name), dpi=600)
                    else:
                        plt.savefig("{}_{}_{}.png".format(trace_name.split('/')[-1], cache_size, name), dpi=600)
                        plt.show()

                    plt.clf()
