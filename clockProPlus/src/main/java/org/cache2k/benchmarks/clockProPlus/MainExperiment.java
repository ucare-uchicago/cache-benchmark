package org.cache2k.benchmarks.clockProPlus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FilenameUtils;

/**
 * This code is part of EV-Store project
 * 
 * @author Daniar Kurniawan
 */

public class MainExperiment {

	private static String BASE_DIR = "/Users/daniar/Documents/Github/cache-benchmark/inf-workload-traces/criteo_kaggle_all_mmap/inference=0.003/";
	private static String BENCH_DIR = "caching_bench/";
	private static String SUMMARY_DIR = "summary/";
	private static String SUMMARY_OUTPUT_DIR = "caching_hit_summary/";

	public static void runSanityTest(ISimpleCache ca) {
		List<String> workloadKeys = Arrays.asList("1", "2", "3", "1", "4", "5", "6", "7", "8", "9", "10");
		for (String key : workloadKeys) {
			ca.request(key);
			System.out.println(key + " :" + ca.toString());
		}
	}

	public static void simpleTest() {
		int size = 4;
		MainExperiment.runSanityTest(new SimpleFIFO(size));
	}

	public static List<String> readFile(Path workloadPath, int rawDataSize) {
		// long startTime = System.nanoTime();
		/**
		 * Check in the header, there should be no comma or space, to make sure that the
		 * file contains only keys. However, the orderedKey workload will contains two
		 * keys.
		 */
		// will get the data from the head and stop at rawDataSize
		int count = 0;
		List<String> workloadKeys = new ArrayList<>();
		try (BufferedReader br = Files.newBufferedReader(workloadPath)) {
			String header = br.readLine();
			System.out.print( header + " ; ");

			if (header.contains(",") == false) {
				// original, without orderedKey
				assertEquals(header.contains(" "), false);
				// workloadKeys = br.lines().collect(Collectors.toList());
				String line;
				while ((line = br.readLine()) != null) {
					workloadKeys.add(line);
					count++;
					if (count == rawDataSize) {
						break;
					}
				}
			} else {
				// with orderedKey. i.e: header = G2_key,orderedKey
				assertEquals(header.contains(",orderedKey"), true);
				int keyIdx = 1; // using the orderedKey
				String line;
				List<String> values;
				while ((line = br.readLine()) != null) {
					// process the line.
					values = Arrays.asList(line.split(","));
					workloadKeys.add(values.get(keyIdx));
					count++;
					if (count == rawDataSize) {
						break;
					}
				}
			}
		} catch (IOException e) {
			System.out.println("ERROR: Got IOException when opening file at " + workloadPath);
			e.printStackTrace();
		}
		// long estimatedTime = System.nanoTime() - startTime;
		// System.out.println(" Estimated reading time: " + estimatedTime /
		// 1_000_000_000. + " s");
		return workloadKeys;
	}

	public static <T> void writeFile(List<T> cacheHitRecord, Path outFilePath, String header) {
		String directory = FilenameUtils.getFullPathNoEndSeparator(outFilePath.toString());
		/** Create parent dirs if it doesn't exist */
		File dirs = new File(directory);
		if (!dirs.exists()) {
			dirs.mkdirs();
		}

		File file = outFilePath.toFile();
		try {
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(header + System.lineSeparator());
			for (T row : cacheHitRecord) {
				bw.write(row + System.lineSeparator());
			}
			bw.close();
		} catch (IOException e) {
			System.out.println("ERROR: Trying to write at " + outFilePath.toString());
			e.printStackTrace();
			System.exit(-1);
		}
		// System.out.println(" Output file = " + outFilePath.toString());
	}

	public static ISimpleCache getCachePolicyInstance(String policyName, int size) {
		return getCachePolicyInstance(policyName, size, 0);
	}

	public static ISimpleCache getCachePolicyInstance(String policyName, int size, int nGroup) {
		ISimpleCache ca;
		// TODO: clean up the unused version
		switch (policyName) {
			case "EvCAR": // Used in paper
				ca = new EvCAR(size, nGroup);
				break;
			case "EvARC": // Used in paper
				ca = new EvARC(size, nGroup);
				break;
			case "SimpleLRU":
				ca = new SimpleLRU(size);
				break;
			case "SimpleLFU":
				ca = new SimpleLFU(size);
				break;
			case "DynamicLIRS":
				ca = new DynamicLIRS(size, new SimpleLIRS.Tuning());
				break;
			case "SimpleLIRS":
				ca = new SimpleLIRS(size, new SimpleLIRS.Tuning());
				break;
			case "SimpleARC":
				ca = new SimpleARC(size);
				break;
			case "CAR":
				ca = new CAR(size);
				break;
			case "Clock":
				ca = new Clock(size);
				break;
			case "ClockLIRS":
				ca = new ClockLIRS(size, new ClockPro.Tuning());
				break;
			case "ClockPro":
				ca = new ClockPro(size, new ClockPro.Tuning());
				break;
			case "SimpleFIFO":
				ca = new SimpleFIFO(size);
				break;
			case "EvLFU":
				// System.out.println("ERROR: This is old version; not used in the paper!");
				// System.exit(-1);
				ca = new EvLFU(size, nGroup);
				break;
			default:
				System.out.println("ERROR: Can't recognize the caching policy " + policyName);
				ca = null;
				System.exit(-1);
				break;
		}
		return ca;
	}

	public static void recordHit(String policyName, int size, List<String> workloadKeys, String outputDir,
			String fullRecordFileName, String summaryFileName) {
		ISimpleCache ca = MainExperiment.getCachePolicyInstance(policyName, size);
		List<Integer> cacheHitRecord = new ArrayList<Integer>();
		List<String> cacheHitSummary = new ArrayList<String>();

		int hitCounter = 0;
		int missCounter = 0;
		for (String key : workloadKeys) {
			if (ca.request(key)) {
				cacheHitRecord.add(1);
				hitCounter++;
			} else {
				cacheHitRecord.add(0);
				missCounter++;
			}
		}
		float hitPercent = ((float) hitCounter / (hitCounter + missCounter) * 100);
		cacheHitSummary.add(hitPercent + "," + (100 - hitPercent));
		cacheHitSummary.add(hitCounter + "," + missCounter);

		/** Write the hit/miss percentage to a file */
		writeFile(cacheHitSummary, Paths.get(BENCH_DIR, outputDir, summaryFileName), "hit,miss");

		/** Write the full record of per key hit/miss on a file */
		writeFile(cacheHitRecord, Paths.get(BENCH_DIR, outputDir, fullRecordFileName), "hit_or_miss");
	}

	/** Check the cache hit rate */
	public static void cacheHitTest(Integer nUniqueKeys, List<String> workloadKeys, Path outputDir, String policyName) {
		int nKeys = workloadKeys.size();
		System.out.println("Total Workload = " + nKeys + " keys");
		System.out.println("Unique Keys    = " + nUniqueKeys + " keys");

		/** Run the cache hit test with incremented capacity percentage */
		float[] capacities = new float[] { 0.01f, 0.02f, 0.04f, 0.08f, 0.16f, 0.32f, 0.64f, 1, 2, 4, 8, 16, 32, 64,
				100 };
		for (float currPercent : capacities) {
			int size = (int) ((float) currPercent / 100 * nUniqueKeys);
			String fullRecordFileName = "";
			String summaryFileName = "";
			if (currPercent >= 1) {
				fullRecordFileName = "fullrecord-" + (int) currPercent + "%.csv";
				summaryFileName = "summary-" + (int) currPercent + "%.csv";
			} else {
				fullRecordFileName = "fullrecord-" + currPercent + "%.csv";
				summaryFileName = "summary-" + currPercent + "%.csv";
			}
			System.out.println("  size = " + size + " ");

			MainExperiment.recordHit(policyName, size, workloadKeys, outputDir.toString(), fullRecordFileName,
					summaryFileName);
		}
	}

	/**
	 * Currently USED
	 */
	public static void singleCacheQueueTest(List<String> arrWorkloadPath,
			String ALGO_NAME, int cacheSize, int benchWorkloadSize, boolean doWarmUp, int warmUpMultiplier) {
		List<List<String>> arrRawWorkloads = new ArrayList<List<String>>();
		List<String> arrMergedBenchWorkloads = new ArrayList<String>();
		int nTableWorkload = arrWorkloadPath.size();
		String cacheHitRecord = "";
		List<String> hitTracesAllTable = new ArrayList<String>();
		String workloadFilesCleaned = String.join("-", arrWorkloadPath).replace(".csv", "").replace("workload-", "")
				.replace("group-", "")
				.replace("cuthead-", "").replace("-1MLOC", "");
		String headerForSummay = workloadFilesCleaned.replace("-", ",") + ",perfect-hit,total-hit,total-workload";
		int countAllData = 0;
		// read all workloads
		System.out.println("Reading Workloads\n   File header = ");
		for (int i = 0; i < nTableWorkload; i++) {
			List<String> workload = readFile(Paths.get(BASE_DIR, arrWorkloadPath.get(i)), benchWorkloadSize);
			arrRawWorkloads.add(workload);
			countAllData += workload.size();
		}
		System.out.println();
		System.out.println("   #arrRawWorkloads table-0 = " + arrRawWorkloads.get(0).size());
		// Make sure that we have enough input workload for warmup and the benchmark
		assertTrue(countAllData >= (benchWorkloadSize * nTableWorkload));
		System.out.println("   #cache-size =  " + (cacheSize));
		// Get the 1D array benchmark workload
		// Assume that the workloads have SAME Length!
		for (int i = 0; i < arrRawWorkloads.get(0).size(); i++) {
			// Start at startBenchWorkload to get the tail of worload for the benchmark
			for (List<String> workloadPerTable : arrRawWorkloads) {
				arrMergedBenchWorkloads.add(workloadPerTable.get(i));
			}
		}
		// nullify the arrRawWorkloads (to save memory)
		arrRawWorkloads = null;

		System.out.println("Done merging ALL workloads; total = " + arrMergedBenchWorkloads.size() + " rows");
		System.out.println("   #benchmark per table =  " + (arrMergedBenchWorkloads.size() / nTableWorkload));
		
		SingleKeyRunnableThd thread;
		System.out.println("Running " + ALGO_NAME + "    ===========");
		if (ALGO_NAME.contains("EvLRU") || ALGO_NAME.contains("EvCAR") || 
        ALGO_NAME.contains("EvLFU") || ALGO_NAME.contains("EvARC")) {
			thread = new MultiKeyRunnableThd("Thread-" + 0,
					getCachePolicyInstance(ALGO_NAME, cacheSize, nTableWorkload),
					arrMergedBenchWorkloads, arrWorkloadPath.size());
		} else {
			thread = new SingleKeyRunnableThd("Thread-" + 0, getCachePolicyInstance(ALGO_NAME, cacheSize),
					arrMergedBenchWorkloads);
		}

		// Do Warm Up
		if (doWarmUp) {
			thread.warmUpTheCache(warmUpMultiplier);
		}

		// Start the benchmark!
		long startTime = System.nanoTime();
		thread.start();
		try {
			// Complete the benchmark, analyze the hit trace
			thread.t.join();

			long estimatedTime = System.nanoTime() - startTime;
			System.out.println("      Estimated running time: " + estimatedTime / 1_000_000_000. + " s");

			ArrayList<Boolean> recordHitMissMerged = thread.getRecordHitMiss();

			List<ArrayList<Boolean>> arrRecordHitMiss = new ArrayList<ArrayList<Boolean>>();
			int[] arrHitCounterAllTable = new int[nTableWorkload];

			// initiate the hit/miss recorder of each table/workload
			for (int i = 0; i < nTableWorkload; i++) {
				arrRecordHitMiss.add(new ArrayList<Boolean>());
				arrHitCounterAllTable[i] = 0;
			}
			// separate the hitmiss record per table
			for (int tableID = 0; tableID < nTableWorkload; tableID++) {
				for (int i = tableID; i < recordHitMissMerged.size(); i += nTableWorkload)
					arrRecordHitMiss.get(tableID).add(recordHitMissMerged.get(i));
			}

			int perfectHit = 0;
			String perRowHitTrace;
			boolean isPerfectHit;
			// analyze the recordHitMiss
			for (int i = 0; i < arrRecordHitMiss.get(0).size(); i++) {
				isPerfectHit = true;
				perRowHitTrace = "";

				for (int tableID = 0; tableID < nTableWorkload; tableID++) {
					if (arrRecordHitMiss.get(tableID).get(i)) {
						arrHitCounterAllTable[tableID]++;
						perRowHitTrace += "1,";
					} else {
						isPerfectHit = false;
						perRowHitTrace += "0,";
					}
				}
				// give the end signature
				perRowHitTrace += "-1";
				hitTracesAllTable.add(perRowHitTrace);

				if (isPerfectHit)
					perfectHit++;
			}

			System.out.println("Perfect Hit = " + perfectHit);
			int totalHit = 0;
			for (int hitCount : arrHitCounterAllTable) {
				cacheHitRecord += hitCount + ",";
				totalHit += hitCount;
			}

			// store the summary to a file
			Path outputFilePath = Paths.get(BASE_DIR, SUMMARY_OUTPUT_DIR, workloadFilesCleaned,
					"cachesize-" + cacheSize + "-single-cachequeue", SUMMARY_DIR, ALGO_NAME + ".csv");
			cacheHitRecord += perfectHit + "," + totalHit + "," + arrRecordHitMiss.get(0).size();
			System.out.println("outputFileName = " + outputFilePath);
			writeFile(Arrays.asList(cacheHitRecord), outputFilePath, headerForSummay);

			System.out.println();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		System.out.println("====================================");
        boolean doWarmUp = true;

		int benchWorkloadSize = 137521;
		int totalCacheSize = 11854;

		List<String> arrWorkloadPath = new ArrayList<String>();

        File folder = new File(MainExperiment.BASE_DIR);
        File[] listOfFiles = folder.listFiles();

        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile() && listOfFiles[i].getName().contains(".csv")) {
                    arrWorkloadPath.add(listOfFiles[i].getName());
            } else if (listOfFiles[i].isDirectory()) {
                // System.out.println("Directory " + listOfFiles[i].getName());
            }
        }
		// Policies: { "EvLFU", "EvCAR", "EvARC" , "SimpleLRU", "SimpleLFU", "SimpleARC", "CAR", "ClockLIRS", "ClockPro","Clock", "SimpleLIRS","DynamicLIRS" };
		String[] policies = new String[] {  "EvLFU", "EvCAR", "EvARC" , "ClockPro"};

		/** 5. Testing single cache queue (with merged workload) of non-MS-LRU */
		for (String ALGO_NAME : policies) {
			MainExperiment.singleCacheQueueTest(arrWorkloadPath, ALGO_NAME, totalCacheSize,
					benchWorkloadSize, doWarmUp, 100);
		}
	}

	/**
	 * NOTE (launch.json): "-Xmx15G" for 80MLOC usage is ~13GB reading time:
	 * 19.933405395 s "-Xmx21G" for FULL 200MLOC usage is ~17GB reading time:
	 * 341.05129926 s
	 */
}
