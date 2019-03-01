package org.ndexbio;

import java.io.PrintStream;
import java.util.Hashtable;
import java.util.UUID;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.RolloverFileOutputStream;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
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
	 private static String ndexServerName;
	 private static GeneSymolIndexer geneSearcher;
	 private static String workingPath;
	 
	 static final Hashtable<UUID, SearchStatus> statusTable = new Hashtable<>();
	 
	 
	  
	  public App() {}

	  public static Hashtable<UUID,SearchStatus> getStatusTable() { return statusTable;}
	  public static String getHostName() { return ndexServerName;}
	  public static GeneSymolIndexer getGeneSearcher() { return geneSearcher;}
	  public static String getWorkingPath() {return workingPath;}
	  
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
		System.out.println("You can use -Dndex.queryport=8285 and -Dndex.fileRepoPrefix=/opt/ndex/data/ -Dndex.host=public.ndexbio.org -Dndex.interactomedb=/opt/ndex/services/interactome to set runtime parameters.");
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
	    final int port = Integer.valueOf(portStr).intValue();
		ndexServerName = System.getProperty("ndex.host", "public.ndexbio.org");
		workingPath = System.getProperty("ndex.interactomedb", "/opt/ndex/services/interactome");
		geneSearcher = new GeneSymolIndexer(workingPath + "/genedb");

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
	      "org.ndexbio.NeighborhoodQueryApplication");
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
