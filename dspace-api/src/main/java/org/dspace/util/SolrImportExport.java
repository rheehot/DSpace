/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.util;

import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.common.SolrDocumentList;
import org.dspace.core.ConfigurationManager;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;

/**
 * Utility class to export, clear and import Solr indexes.
 * @author Andrea Schweer schweer@waikato.ac.nz for the LCoNZ Institutional Research Repositories
 */
public class SolrImportExport
{

	public static final String HELP_OPTION = "h";
	public static final String INDEX_NAME_OPTION = "i";
	public static final String ACTION_OPTION = "a";
	public static final String DIRECTORY_OPTION = "d";

	public static final int ROWS_PER_FILE = 10_000;

	private static final Logger log = Logger.getLogger(SolrImportExport.class);

	/**
	 * Entry point for command-line invocation
	 * @param args command-line arguments; see help for description
	 * @throws ParseException if the command-line arguments cannot be parsed
	 */
	public static void main(String[] args) throws ParseException
	{
		CommandLineParser parser = new PosixParser();
		Options options = new Options();
		options.addOption(HELP_OPTION, "help", false, "Get help on options for this command.");
		options.addOption(INDEX_NAME_OPTION, "index-name", true,
				                 "The names of the indexes to process. At least one is required. Available indexes are: authority, statistics.");
		options.addOption(ACTION_OPTION, "action", true,
				                 "The action to perform: import, export or clear. If none is given, export will be used.");
		options.addOption(DIRECTORY_OPTION, "directory", true,
				                 "The absolute path for the directory to use for import or export. If none is given, [dspace]/solr-export is used.");

		try
		{
			CommandLine line = parser.parse(options, args);
			if (line.hasOption(HELP_OPTION))
			{
				printHelpAndExit(options, 0);
			}

			if (!line.hasOption(INDEX_NAME_OPTION))
			{
				System.err.println("This command requires the index-name option but none was present.");
				printHelpAndExit(options, 1);
			}
			String[] indexNames = line.getOptionValues(INDEX_NAME_OPTION);

			String action = line.getOptionValue(ACTION_OPTION, "export");
			if ("import".equals(action))
			{
				for (String indexName : indexNames)
				{
					try
					{
						importIndex(indexName, makeDirectoryName(line.getOptionValue(DIRECTORY_OPTION)));
					}
					catch (IOException | SolrServerException e)
					{
						System.err.println("Problem encountered while trying to import index " + indexName + ".");
						e.printStackTrace(System.err);
					}
				}
			}
			else if ("export".equals(action))
			{
				for (String indexName : indexNames)
				{
					try
					{
						exportIndex(indexName, makeDirectoryName(line.getOptionValue(DIRECTORY_OPTION)));
					}
					catch (SolrServerException | IOException | SolrImportExportException e)
					{
						System.err.println("Problem encountered while trying to export index " + indexName + ".");
						e.printStackTrace(System.err);
					}
				}
			}
			else if ("clear".equals(action))
			{
				for (String indexName : indexNames)
				{
					try
					{
						clearIndex(indexName);
					}
					catch (IOException | SolrServerException e)
					{
						System.err.println("Problem encountered while trying to clear index " + indexName + ".");
						e.printStackTrace(System.err);
					}
				}
			}
			else
			{
				System.err.println("Unknown action " + action + "; must be import, export or clear.");
				printHelpAndExit(options, 1);
			}
		}
		catch (ParseException e)
		{
			System.err.println("Cannot read command options");
			printHelpAndExit(options, 1);
		}
	}

	/**
	 *
	 * @param indexName the index to import.
	 * @param fromDir the source directory.
	 *                   The importer will look for files whose name starts with <pre>indexName</pre>
	 *                   and ends with .csv (to match what is generated by #makeExportFilename).
	 * @throws IOException if there is a problem reading the files or communicating with Solr.
	 * @throws SolrServerException if there is a problem reading the files or communicating with Solr.
	 */
	public static void importIndex(final String indexName, String fromDir) throws IOException, SolrServerException
	{
		String solrUrl = getSolrUrl(indexName);
		HttpSolrServer solr = new HttpSolrServer(solrUrl);

		File sourceDir = new File(fromDir);
		File[] files = sourceDir.listFiles(new FilenameFilter()
		{
			@Override
			public boolean accept(File dir, String name)
			{
				return name.startsWith(indexName) && name.endsWith(".csv");
			}
		});

		if (files == null || files.length == 0)
		{
			log.warn("No export files found in directory " + fromDir + " for index " + indexName);
			return;
		}

		for (File file : files)
		{
			log.info("Importing file " + file.getCanonicalPath());
			ContentStreamUpdateRequest contentStreamUpdateRequest = new ContentStreamUpdateRequest("/update/csv");
			contentStreamUpdateRequest.setParam("skip", "_version_");
			contentStreamUpdateRequest.setParam("stream.contentType", "text/plain;charset=utf-8");
			contentStreamUpdateRequest.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);
			contentStreamUpdateRequest.addFile(file, "text/plain;charset=utf-8");

			solr.request(contentStreamUpdateRequest);
		}

		solr.commit(true, true);
	}

	/**
	 * Remove all documents from the Solr index with the given name, then commit and optimise the index.
	 * @see #getSolrUrl(String) for the logic to determine the Solr URL from the index name.
	 *
	 * @param indexName The index to clear.
	 * @throws IOException if there is a problem in communicating with Solr.
	 * @throws SolrServerException if there is a problem in communicating with Solr.
	 */
	public static void clearIndex(String indexName) throws IOException, SolrServerException
	{
		String solrUrl = getSolrUrl(indexName);
		HttpSolrServer solr = new HttpSolrServer(solrUrl);
		solr.deleteByQuery("*:*");
		solr.commit();
		solr.optimize();
	}

	/**
	 * Exports all documents in the given index to the specified target directory in batches of #ROWS_PER_FILE.
	 * See #makeExportFilename for the file names that are generated.
	 *
	 * @param indexName The index to export.
	 * @param toDir The target directory for the export.
	 * @throws SolrServerException if there is a problem with exporting the index.
	 * @throws IOException if there is a problem creating the files or communicating with Solr.
	 * @throws SolrImportExportException if there is a problem in communicating with Solr.
	 */
	public static void exportIndex(String indexName, String toDir) throws SolrServerException, IOException, SolrImportExportException
	{
		File targetDir = new File(toDir);
		if (!targetDir.exists())
		{
			//noinspection ResultOfMethodCallIgnored
			targetDir.mkdirs();
		}
		if (!targetDir.exists())
		{
			throw new SolrImportExportException("Could not create target directory " + toDir + ", aborting export of index ");
		}

		SolrQuery query = new SolrQuery("*:*");

		String solrUrl = getSolrUrl(indexName);
		if (StringUtils.isBlank(solrUrl))
		{
			throw new SolrImportExportException("Could not construct solr URL for index" + indexName + ", aborting export.");
		}

		HttpSolrServer solr = new HttpSolrServer(solrUrl);
		SolrDocumentList results = solr.query(query).getResults();
		long totalRecords = results.getNumFound();

		query.setRows(ROWS_PER_FILE);
		query.set("wt", "csv");
		query.set("fl", "*");
		for (int i = 0; i < totalRecords; i+= ROWS_PER_FILE) {
			query.setStart(i);
			URL url = new URL(solrUrl + "/select?" + query.toString());

			File file = new File(targetDir.getCanonicalPath(), makeExportFilename(indexName, totalRecords, i));
			if (file.createNewFile())
			{
				FileUtils.copyURLToFile(url, file);
				log.info("Exported batch " + i + " to " + file.getCanonicalPath());
			}
			else
			{
				log.error("Could not create file " + file.getCanonicalPath() + " while exporting index " + indexName + ", batch " + i);
			}
		}
	}

	/**
	 * Return the specified directory name or fall back to a default value.
	 *
	 * @param directoryValue a specific directory name. Optional.
	 * @return directoryValue if given as a non-blank string. A default directory otherwise.
	 */
	private static String makeDirectoryName(String directoryValue)
	{
		if (StringUtils.isNotBlank(directoryValue))
		{
			return directoryValue;
		}
		return ConfigurationManager.getProperty("dspace.dir") + File.separator + "solr-export" + File.separator;
	}

	private static String makeExportFilename(String indexName, long totalRecords, int index)
	{
		return indexName
				       + "_export_"
				       + StringUtils.leftPad("" + index, (int) Math.log10(totalRecords / ROWS_PER_FILE) + 1, "0")
				       + ".csv";
	}

	/**
	 * Returns the full URL for the specified index name.
	 *
	 * @param indexName the index name whose Solr URL is required. If the index name starts with
	 *                     &quot;statistics&quot; or is &quot;authority&quot;, the Solr base URL will be looked up
	 *                     in the corresponding DSpace configuration file. Otherwise, it will fall back to a default.
	 * @return the full URL to the Solr index, as a String.
	 */
	private static String getSolrUrl(String indexName)
	{
		if (indexName.startsWith("statistics"))
		{
			// TODO account for year shards properly
			return ConfigurationManager.getProperty("solr-statistics", "server") + indexName.replaceFirst("statistics", "");
		}
		else if ("authority".equals(indexName))
		{
			return ConfigurationManager.getProperty("solr.authority.server");
		}
		return "http://localhost:8080/solr/" + indexName; // TODO better default?
	}

	/**
	 * A utility method to print out all available command-line options and exit given the specified code.
	 *
	 * @param options the supported options.
	 * @param exitCode the exit code to use. The method will call System#exit(int) with the given code.
	 */
	private static void printHelpAndExit(Options options, int exitCode)
	{
		HelpFormatter myhelp = new HelpFormatter();
		myhelp.printHelp(SolrImportExport.class.getSimpleName() + "\n", options);
		System.exit(exitCode);
	}
}
