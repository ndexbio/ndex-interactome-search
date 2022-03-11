# NDEx Interactome Search service
Interactome search service for NDEx [iQuery](#http://iquery.ndexbio.org). This service has two components. 

1. **Interactome search**: find subnetworks that relate to your query gene set from a collection 
of protein protein interaction networks.
2. **Gene association search**: find subnetworks that relate to your query gene set from a collection 
of gene association networks such as gene-chemical assocation networks and gene-disease association 
networks etc. 

# Build the service

**Requirements**:

* Java 11+ JDK
* Maven 3.6 or higher 

Commands below build Interactome Service assuming machine has Git command line tools installed and 
above Java modules have been installed:

```Bash
# In lieu of git one can just download repo and unzip it
git clone https://github.com/cytoscape/ndex-interactome-search.git

cd /ndex-interactome-search
mvn clean test install
```

The above command will create a jar file under target/ named
interactomeSearch-<VERSION>.jar that is a command line application

Deloy, Configure and Running NDEx Interactome Search REST Service
============================================

### Step 1 Deploy the service

For simplcity, we recommend deploying this service on the same server that your NDEx server is running on. 
If you want to scale up this service, you can also deploy this service to a seperate server, as long as the
the file system of your NDEx server is mounted to that server. On the target server, create directory 
interactome and copy interactomeSearch-<VERSION>.jar into that directory.

```Bash
# change to the interactome search directory
cd interactome

# create directory
mkdir logs 
```

### Step 2 Build the index for Interactome Search

Create an configuration file. This configuration file is in JSON format and has two lists in it.
Attribute **associationNetworks** contains a list of gene association networks that this service will search. 
Attribute **ppiNetworks** contains a list of protein-protein interaction networks that the service will 
search. The value of attribute **UUID** is the UUID of the network stored in your NDEx server. Here is an
example of networks.json file:

```Bash
cat networks.json
{
	"associationNetworks": [
		{"uuid":"109a36a9-a4d2-11e9-8818-525400c25d22"},
		{"uuid":"0e00c717-a4d2-11e9-8818-525400c25d22"},
		{"uuid":"0498a7f8-a756-11e9-8818-525400c25d22"},
		{"uuid":"92e74213-a4d1-11e9-8818-525400c25d22"},
		{"uuid":"8b1eeaf1-a4d1-11e9-8818-525400c25d22"}
	],
	"ppiNetworks": [
    {"uuid":"d5a8d887-a816-11e9-85eb-525400c25d22"},
		{"uuid":"3dcf562a-a80d-11e9-85eb-525400c25d22"},
		{"uuid":"f3015235-a80a-11e9-85eb-525400c25d22"},
		{"uuid":"aeb77786-a4d1-11e9-8818-525400c25d22"},
		{"uuid":"bc1ce496-1b82-11ea-a0f6-525400c25d22"}
	]
}
```
After creating the configuration file, build the index using this command

```Bash
java -classpath interactomeSearch-<VERSION>.jar org.ndexbio.interactomesearch.GeneSymbolIndexer ./genedb /opt/ndex/data/ networks.json public.ndexbio.org
```
*genedb* is the name of the database you created for interactome search.  */opt/ndex/data/* is the path of
the NDEx network data storage.  *networks.json* is the name of configuration file. *public.ndexbio.org* is the host name of
the NDEx server.

### Step 3 Start the Interactome Search service
You can use this command to start the service:

```Bash
nohup java -Xmx1g -Dndex.host="http://dev.ndexbio.org/v2" -Dndex.interactomehost=dev.ndexbio.org -Dndex.interactomedb=/opt/ndex/services/interactome -Dndex.queryport=8287 
-jar interactomeSearch-<VERSION>.jar & 1>out
```
The service supports the following command line parameters:
* **ndex.host** Host name of the NDEx server that this service search on. Default value is *public.ndexbio.org*.
* **ndex.interactomehost** Host name of this interactome service. Default value is *loalhost*.
* **ndex.interactomedb** Path of the interactome directory. Default value is */opt/ndex/services/interactome*.
* **ndex.queryport** Port number of this service. Default value is *8285*.
* **ndex.fileRepoPrefix** Path of the NDEx network file storage. Default value is */opt/ndex/data/*.


Stop the NDEx Interactome Search REST Service
============================================

```Bash
sudo -u ndex /bin/bash # become ndex user
ps -elf | grep interactome
kill <PID of java process for interactome output from previous step>
```

Rebuild the index
=================
If you edit the configuration file or any of the networks you used in the search is modified in NDEx server,
You need to rebuild the index of this service. To rebuild the index.
1. Stop the interactome service
2. Delete the genedb directory in the interactome directory
3. Run the create index command.
4. Start the interactome service.
