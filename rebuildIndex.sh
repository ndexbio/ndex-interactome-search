#!/bin/bash

if [ $# != 2 ]; then 
   echo "This script needs 2 arguments. Example:"
   echo "rebuldIndex.sh <UUID> <network_type>"
   echo "network_type can be i or a"
   exit 0
else   
  java -classpath interactomeSearch-0.1.0.jar org.ndexbio.interactomesearch.GeneSymbolIndexer /opt/ndex/services/interactome/genedb /opt/ndex/data/ "$1" "$2"
fi
