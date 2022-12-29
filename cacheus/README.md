## Cacheus
Cacheus code is based from [this repo](https://github.com/sylab/cacheus/)
### Experiments

To run the experiment follow these steps

1. Make sure that python 3 is installed with numpy library

2. Generate inference traces at ev-store-dlrm repo
   Follow this section "Generate Workload For Java CacheBench" inside its Readme
   output = logs/inf-workload-traces/criteo_kaggle_all_mmap/inference=0.0022/

3. Download traces from ev-store-dlrm repo [run in local]
   
   cd $GITHUB/cache-benchmark/
   mkdir -p inf-workload-traces/
   cd $GITHUB/cache-benchmark/inf-workload-traces/
   rsync -Pav daniar@192.5.86.175:/mnt/extra/ev-store-dlrm/logs/inf-workload-traces/ .

4. Change the `example.config` file to adjust to your local path
   {
   	"output_csv": "output/results.csv",
   	"cache_sizes": [32768],
   	"traces": ["[absolute path to this repo in your machine]/cacheus/Workload"],

      	"algorithms": ["cacheus"]
   }
  # "traces": ["/Users/daniar/Documents/EV-Store/datasets/criteo/workload/day-2/1MLOC/"],

5. Run the following command in the terminal
    cd $GITHUB/cache-benchmark/cacheus
    python3 run.py example.config




============================================================================================
2. Unzip `Workload.zip`, there should be a new folder in the github project called Workload