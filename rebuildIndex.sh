#!/bin/bash

for arg in "$@"
do
java -classpath interactomeSearch-0.1.0.jar org.ndexbio.interactomesearch.GeneSymbolIndexer /opt/ndex/services/interactome/genedb /opt/ndex/data/ "$arg"
done
