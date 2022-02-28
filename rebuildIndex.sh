#!/bin/bash

echo "This script looks for and kills the 3 services"
echo "for integrated search aka iquery "
echo "(enrichment, interactome, integrated search)"
echo ""
echo "The script then rebuilds the index for enrichment"
echo "and interactome. The process takes 10-15+ minutes"
echo ""
echo "At the end the services are restarted"
echo ""
echo "Sleeping 20 seconds. If you dont want to do this"
echo "Hit Ctrl-c now!!!!!!"
echo ""
sleep 20

echo "Done sleeping, starting..."
#
# Gets id for process with string passed in as
# argument. There is a filter where the
# process should contain .jar in name
# The result is sent to standard out
#
function get_id_for_process()
{
  ps -elf | grep "$1" | grep "\.jar" | sed "s/\s\+/ /g" | cut -d' ' -f 4
}

# attempts to kill process if it exists
function kill_process()
{ 
  if [ -z "$2" ] ; then
     echo "No $1 process id to kill"
     return 0
  fi
  echo "Running kill $2"
  kill $2
  if [ $? != 0 ] ; then
     echo "   Error killing process: $1 ($2) "
     return 1
  fi
  echo "   Sleeping 5 to wait for shutdown"
  sleep 5 
  ps --pid $2 -o pid 1> /dev/null
  if [ $? == 0 ] ; then
     echo "   Process $1 ($2) did not die"
     return 1
  fi
  return 0
}

rawscriptdir=`dirname $0`

cd $rawscriptdir
SCRIPT_DIR=`pwd -P`

if [ `whoami` != "ndex" ] ; then
   echo "ERROR This script should be run by user ndex and not `whoami`"
   exit 1
fi

# kill search
search_proc=$(get_id_for_process ndexsearch-rest)
kill_process ndexsearch-rest "$search_proc"

# kill interactome
interactome_proc=$(get_id_for_process interactomeSearch)
kill_process interactomeSearch "$interactome_proc"

# kill enrichment
enrich_proc=$(get_id_for_process ndex-enrichment-rest)
kill_process ndex-enrichment-rest "$enrich_proc"

# run updatedb.sh in enrich directory
pushd enrichment 1> /dev/null
echo "Running ./updatedb.sh"
./updatedb.sh
echo "Starting enrichment ./run.sh"
./run.sh
popd 1> /dev/null

pushd interactome 1> /dev/null
echo "Running ./rebuildIndex.sh"
./rebuildIndex.sh
echo "Starting interactome ./run.sh"
./run.sh
popd 1> /dev/null

echo "Sleeping 5 to wait for startup"
sleep 5

pushd search 1> /dev/null
echo "Starting search ./run.sh"
./run.sh
popd 1> /dev/null

