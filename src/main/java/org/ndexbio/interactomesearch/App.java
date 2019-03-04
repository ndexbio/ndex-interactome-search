package org.ndexbio.interactomesearch;

import java.io.File;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Hashtable;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.RolloverFileOutputStream;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.ndexbio.interactomesearch.object.NetworkShortSummary;
import org.ndexbio.interactomesearch.object.SearchStatus;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.client.NdexRestClient;
import org.ndexbio.rest.client.NdexRestClientModelAccessLayer;
import org.slf4j.Logger;

import ch.qos.logback.classic.Level;

import org.eclipse.jetty.util.log.Log;

/**
 *
 */
public class App 
{
	

	 static final String APPLICATION_PATH = "/interactome";
	 static final String CONTEXT_ROOT = "/";
	 private static String ndexServerName;   //Ndex server host which has these interactome networks
	 private static GeneSymolIndexer geneSearcher;
	 private static String workingPath;  // working directory of this service.
	 private static String serviceHost;  // host name of this service
	 private static int port;    //service port.
	 
	 private static final Hashtable<String, NetworkShortSummary> dbTable = new Hashtable<>();
	 
	 // task ID to status table
	 private static final Hashtable<UUID, SearchStatus> statusTable = new Hashtable<>();  
	 
	 
	  
	  public App() {}

	  public static Hashtable<UUID,SearchStatus> getStatusTable() { return statusTable;}
	  //public static String getHostName() { return ndexServerName;}
	  public static GeneSymolIndexer getGeneSearcher() { return geneSearcher;}
	  public static String getWorkingPath() {return workingPath;}
	  public static String getServiceHost() {return serviceHost;}
	  public static int getPort() { return port;}
	  public static Hashtable<String, NetworkShortSummary> getDBTable() { return dbTable;}
	  
	  public static void main( String[] args ) throws Exception
	  {
		  

			
	    try
	    {
	      run();
	    }
	    catch (Throwable t)
	    {
	      t.printStackTrace();
	    }
	  }
	  
	  public static void run() throws Exception
	  {
		System.out.println("You can use -Dndex.queryport=8285 and -Dndex.fileRepoPrefix=/opt/ndex/data/ -Dndex.host=public.ndexbio.org -Dndex.interactomedb=/opt/ndex/services/interactome + "
				+ "\n        -Dndex.interactomehost=localhost to set runtime parameters.");
		ch.qos.logback.classic.Logger rootLog = 
        		(ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		rootLog.setLevel(Level.INFO);
		
		//We are configuring a RolloverFileOutputStream with file name pattern  and appending property
		RolloverFileOutputStream os = new RolloverFileOutputStream("logs/queries_yyyy_mm_dd.log", true);
		
		//We are creating a print stream based on our RolloverFileOutputStream
		PrintStream logStream = new PrintStream(os);

		//We are redirecting system out and system error to our print stream.
		System.setOut(logStream);
		System.setErr(logStream);	  		
		
		
		String portStr = System.getProperty("ndex.queryport", "8285")  ;
		String serverFileRepoPrefix = System.getProperty("ndex.fileRepoPrefix", "/opt/ndex/data/");
	    port = Integer.valueOf(portStr).intValue();
		ndexServerName = System.getProperty("ndex.host", "public.ndexbio.org");
		workingPath = System.getProperty("ndex.interactomedb", "/opt/ndex/services/interactome");
		geneSearcher = new GeneSymolIndexer(workingPath + "/genedb");
		serviceHost = System.getProperty("ndex.interactomehost", "localhost");

		//remove the old results first
		FileUtils.deleteDirectory(new File(workingPath + "/result"));
		
		// initialize the table of interactome networks
		NdexRestClientModelAccessLayer ndex = 
				new NdexRestClientModelAccessLayer(new NdexRestClient(null, null, ndexServerName));

        Collection<NetworkSummary> nets = 
        		ndex.getNetworkSummariesByIds(App.getGeneSearcher().getUUIDsFromDB().stream().map( e -> UUID.fromString(e)).collect(Collectors.toList()));
        for ( NetworkSummary summary : nets) {
        	NetworkShortSummary rec = new NetworkShortSummary();
        	rec.setName(summary.getName());
        	rec.setDescription(summary.getDescription());
        	rec.setEdgeCount(summary.getEdgeCount());
        	rec.setNodeCount(summary.getNodeCount());
        	rec.setURL("http://"+ndexServerName+"/v2/network/"+ summary.getExternalId() );
        	dbTable.put(summary.getExternalId().toString(),rec);
        }
		
	    NetworkQueryManager.setDataFilePathPrefix(serverFileRepoPrefix);
	    final Server server = new Server(port);
	    
	    rootLog.info("Server started on port " + portStr  + ", with network data repo at " + serverFileRepoPrefix);

	    // Setup the basic Application "context" at "/".
	    // This is also known as the handler tree (in Jetty speak).
	    final ServletContextHandler context = new ServletContextHandler(
	      server, CONTEXT_ROOT);

	    // Setup RESTEasy's HttpServletDispatcher at "/api/*".
	    final ServletHolder restEasyServlet = new ServletHolder(
	      new HttpServletDispatcher());
	    restEasyServlet.setInitParameter("resteasy.servlet.mapping.prefix",
	      APPLICATION_PATH);
	    restEasyServlet.setInitParameter("javax.ws.rs.Application",
	      "org.ndexbio.interactomesearch.InteractomeSearchApplication");
	    context.addServlet(restEasyServlet, APPLICATION_PATH + "/*");

	    // Setup the DefaultServlet at "/".
	    final ServletHolder defaultServlet = new ServletHolder(
	      new DefaultServlet());
	    context.addServlet(defaultServlet, CONTEXT_ROOT);

	    server.start();
	  //Now we are appending a line to our log 
	  	Log.getRootLogger().info("Embedded Jetty logging started.", new Object[]{});
	    
	    System.out.println("Server started on port " + port + ", with network data repo at " + serverFileRepoPrefix);
	    server.join();
	    
	  } 
}
