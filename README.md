**Description**
-----------
This is a supplementary material for the [EVStore](https://github.com/ucare-uchicago/ev-store-dlrm) paper. This repo contains experiment that focuses on cache benchmarking experiments.

<br>

------------------------------------------------------------------
## **Run Cacheus Cache Benchmark**
------------------------------------------------------------------

> Follow: [Cacheus Readme](cacheus/README.md)


<br>

------------------------------------------------------------------
## **Run Cache Benchmark on Local Machine** 
------------------------------------------------------------------

1. Uncompress the cache2k.zip
2. Put it in the maven repo, by default it should be at ~/.m2/repository/org/
3. The content of that cache2k should be here: ~/.m2/repository/org/cache2k/*
4. The main file is at cache-benchmark/clockProPlus/src/main/java/org/cache2k/benchmarks/clockProPlus/MainExperiment.java

    ``` bash
    # Edit the following variables inside MainExperiment.java:
    
	BASE_DIR = "/Users/daniar/Documents/Github/cache-benchmark/inf-workload-traces/criteo_kaggle_all_mmap/inference=0.003/";
	BENCH_DIR = "caching_bench/";
	SUMMARY_DIR = "summary/";
	SUMMARY_OUTPUT_DIR = "caching_hit_summary/";

    benchWorkloadSize = 137521;
    totalCacheSize = 11854;
    policies = new String[] {  "EvLFU", "EvCAR", "EvARC" , "ClockPro"};
    ```

<br>

------------------------------------------------------------------
## **Run Cache Benchmark on Chameleon (CPU Node)**
------------------------------------------------------------------

> You can reserve any Skylake/Haswell/Cascadelake node on Chameleon Cloud
 
### Create Reservation
> Make sure the lease lasts for maximum 7 days through the lease menu


### Launching an Instance
> Use CC-Ubuntu20.04 as the node's operating system, for more information please refer to the Chameleon Cloud documentation

### Allocate floating IPs
> Allocate an IP address for the node so that it can be accessed through the Internet via SSH

### 1. Preparation [Login as "cc"]
- Use cc user
    ```bash
    ssh cc@<IP_address>     # use your IP address
    ```
- Check if it uses SSD
    ```bash
    lsblk -o name,rota      # 0 means SSD
    ```
- Setup disk <br> 
    Check if there is already mounted disk
    ```bash
    df -H
        # sda      8:0    0 223.6G  0 disk
        # should be enough
    ```
    Check SSD or Disk
    ```bash
    lsblk -d -o name,rota
    cat /sys/block/sda/queue/rotational
    ```
- Setup user 
    ```bash
    sudo adduser --disabled-password --gecos "" evstoreuser
    sudo usermod -aG sudo evstoreuser
    sudo su 
    cp -r /home/cc/.ssh /home/evstoreuser
    chmod 700  /home/evstoreuser/.ssh
    chmod 644  /home/evstoreuser/.ssh/authorized_keys
    chown evstoreuser  /home/evstoreuser/.ssh
    chown evstoreuser  /home/evstoreuser/.ssh/authorized_keys
    echo "evstoreuser ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers.d/90-cloud-init-users
    exit
    exit
    ```

### 2. Setup zsh [Login on "user"]
```bash
ssh evstoreuser@<IP_address>
```
```bash
sudo su
apt-get update
apt-get install zsh -y
chsh -s /bin/zsh root
# Break the Copy here ====
```
```bash
exit
sudo chsh -s /bin/zsh evstoreuser
which zsh
echo $SHELL
sudo apt-get install wget git vim zsh -y
# Break the Copy here ====
```
```bash
printf 'Y' | sh -c "$(wget -O- https://raw.githubusercontent.com/ohmyzsh/ohmyzsh/master/tools/install.sh)"
/bin/cp ~/.oh-my-zsh/templates/zshrc.zsh-template ~/.zshrc
sudo sed -i 's|home/evstoreuser:/bin/bash|home/evstoreuser:/bin/zsh|g' /etc/passwd
sudo sed -i 's|ZSH_THEME="robbyrussell"|ZSH_THEME="risto"|g' ~/.zshrc
zsh
exit
exit
```

<br>

------------------------------------------------------------------
## **Run Cache Benchmark (EV-Store Java Version) on Chameleon**
------------------------------------------------------------------

### 1. Clone the repo 
```bash
cd ~
git clone https://github.com/daniarherikurniawan/cache-benchmark.git
```

### 2. Install dependencies
```bash
# Install the default Java Runtime Environment (JRE) and JDK 
cd ~/cache-benchmark		
sudo apt update
sudo apt install default-jre
sudo apt install default-jdk

# install maven
sudo apt install maven

mvn help:evaluate -Dexpression=settings.localRepository

# Make sure this folder already exists:
# ~/.m2/repository
cp ~/cache-benchmark/repository/* ~/.m2/repository/
```
### 3. Run cache-benchmark

- Preparation (get the workload)
    Follow: [Set up Criteo Kaggle workloads for Java Cache-benchmark](#set-up-criteo-kaggle-workloads-for-java-cache-benchmark)

- Do benchmark 
    ```bash
    cd ~/cache-benchmark
    # Make sure the dataset path in MainExperiment.java matches yours  
    # MainExperiment.BASE_DIR = "/home/evstoreuser/workload/workload_5mill_kaggle/" in main function;
    chmod +x benchmark.sh
    
    # Change the parameters in MainExperiment.java at main() as needed, then run:

    cd ~/cache-benchmark
    ./benchmark.sh
    ```

<br>

------------------------------------------------------------------
## **Set up Criteo Kaggle workloads for Java Cache-benchmark**
------------------------------------------------------------------
### Converting raw Criteo Kaggle dataset to Java cache-benchmark workload

### 1. Get the raw dataset 
```bash
mkdir ~/workload
cd ~/workload
wget http://go.criteo.net/criteo-research-kaggle-display-advertising-challenge-dataset.tar.gz
tar -xzvf criteo-research-kaggle-display-advertising-challenge-dataset.tar.gz
# Use partial dataset!
head -n 5000000 train.txt > train_5mill.csv
```
### 2. Convert raw dataset to Java cache-benchmark workload
```bash
# create output directory for new workloads
mkdir ~/workload/workload_5mill_kaggle

# Change inFile and outFile like yours 
# inFile : input path for raw dataset
# outDir : output directory for new workloads generated
python ~/cache-benchmark/script/converter.py --inFile ~/workload/train_5mill.csv --outDir ~/workload/workload_5mill_kaggle
```

Contributors
-------------
- Daniar Kurniawan
- Ruipu (Rex) Wang
- Fandi Wiranata
- Kahfi Zulkifli
- Garry Kuwanto
 

Acknowledgement
---------------
This code is based on:
- Cache2k (https://github.com/cache2k/cache2k)
- Cacheus (https://github.com/sylab/cacheus/)
