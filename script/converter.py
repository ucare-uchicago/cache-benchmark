import pandas as pd
import numpy as np
import csv
import os
import argparse


if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="Converting raw Criteo Kaggle dataset to Java cache-benchmark workload")
    parser.add_argument("--inFile", type=str, default="")
    parser.add_argument("--outDir", type=str, default="")

    args = parser.parse_args()
    
    df = pd.read_csv (args.inFile, header = None, dtype=object, delimiter='\t')
    # Remove column 0-13
    df.drop(df.loc[:, 0:13], inplace = True, axis = 1)
    # replace Nan to None
    df = df.where(pd.notnull(df), "None")
    #print(df)
    column_values = df.loc[:, 14:39].values.ravel()
    unique_values =  pd.unique(column_values)
    #print(unique_values)

    print("number of unique values ALL TABLES =", len(unique_values))
    arrUniqueValPerTable = []
    for idx in range(0, len(df.columns)):
        curCol = df.columns[idx]
        colmVal = df.loc[:, curCol]
        #print(colmVal)
        uniquePerTable = pd.unique(colmVal)
        arrUniqueValPerTable.append(len(uniquePerTable)) 
    print("Number of unique values per table =",arrUniqueValPerTable)
    print("============================================================================================")
    #exit(1)

    for idx in range(0, len(df.columns)):
        curCol = df.columns[idx]
        headerFile = "G"+str(idx+1)+"_key"
        outFile = os.path.join(args.outDir, "workload-group-" + str(idx + 1) + ".csv")
        df[curCol].to_csv(outFile, header=[headerFile], index=False)
        print("OutFile:", outFile)
    