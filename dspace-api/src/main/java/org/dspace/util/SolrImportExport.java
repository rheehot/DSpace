package org.dspace.util;

import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.common.SolrDocumentList;
import org.dspace.core.ConfigurationManager;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for the LCoNZ Institutional Research Repositories
 */
public class SolrImportExport
{

	public static final String HELP_OPTION = "h";
	public static final String INDEX_NAME_OPTION = "i";
	public static final String ACTION_OPTION = "a";
	public static final String DIRECTORY_OPTION = "d";
	public static final int ROWS_PER_FILE = 10_000;

	public static void main(String[] args) throws ParseException
	{
		CommandLineParser parser = new PosixParser();
		Options options = new Options();
		options.addOption(HELP_OPTION, "help", false, "Get help on options for this command.");
		options.addOption(INDEX_NAME_OPTION, "index-name", true,
				                 "The names of the indexes to process. At least one is required. Available indexes are: authority, statistics.");
		options.addOption(ACTION_OPTION, "action", true,
				                 "The action to perform: import, export or clear. If none is given, export will be used.");

		try {
			CommandLine line = parser.parse(options, args);
			if (line.hasOption(HELP_OPTION)) {
				printHelpAndExit(options, 0);
			}
			if (!line.hasOption(INDEX_NAME_OPTION)) {
				System.out.println("This command requires the index-name option but none was present.");
				printHelpAndExit(options, 1);
			}
			String[] indexNames = line.getOptionValues(INDEX_NAME_OPTION);
			String action = line.getOptionValue(ACTION_OPTION, "export");
			if ("import".equals(action)) {
				for (String indexName : indexNames) {
					try {
						importIndex(indexName);
					} catch (IOException | SolrServerException e) {
						e.printStackTrace(System.err);
					}
				}
			} else if ("export".equals(action)) {
				for (String indexName : indexNames) {
					try {
						exportIndex(indexName);
					} catch (SolrServerException | IOException e) {
						e.printStackTrace(System.err);
					}
				}
			} else if ("clear".equals(action))
			{
				for (String indexName : indexNames)
				{
					try {
						clearIndex(indexName);
					} catch (IOException | SolrServerException e) {
						e.printStackTrace(System.err);
					}
				}
			}
			else
			{
				System.out.println("Unknown action " + action + "; must be import, export or clear.");
				printHelpAndExit(options, 1);
			}
		}
		catch (ParseException e)
		{
			System.err.println("Cannot read command options");
			printHelpAndExit(options, 1);
		}
	}

	private static void importIndex(String indexName) throws IOException, SolrServerException {
		String solrUrl = getSolrUrl(indexName);
		HttpSolrServer solr = new HttpSolrServer(solrUrl);

		File sourceDir = new File(ConfigurationManager.getProperty("dspace.dir") + File.separator + "solr-export-" + indexName + File.separator);
		File[] files = sourceDir.listFiles();
		if (files == null || files.length == 0) {
			return; // TODO complain
		}
		for (File file : files) {
			// feed rewritten file into solr
			ContentStreamUpdateRequest contentStreamUpdateRequest = new ContentStreamUpdateRequest("/update/csv");
			contentStreamUpdateRequest.setParam("skip", "_version_");
			contentStreamUpdateRequest.setParam("stream.contentType", "text/plain;charset=utf-8");
			contentStreamUpdateRequest.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);
			contentStreamUpdateRequest.addFile(file, "text/plain;charset=utf-8");

			solr.request(contentStreamUpdateRequest);
		}

		solr.commit(true, true);
	}

	private static void clearIndex(String indexName) throws IOException, SolrServerException {
		String solrUrl = getSolrUrl(indexName);
		HttpSolrServer solr = new HttpSolrServer(solrUrl);
		solr.deleteByQuery("*:*");
		solr.commit();
		solr.optimize();
	}

	private static void exportIndex(String indexName) throws SolrServerException, IOException {
		File targetDir = new File(ConfigurationManager.getProperty("dspace.dir") + File.separator + "solr-export-" + indexName + File.separator);
		if (targetDir.exists()) {
			// TODO complain
			return;
		}
		boolean dirCreated = targetDir.mkdirs();
		if (!dirCreated) {
			// TODO complain
			return;
		}

		SolrQuery query = new SolrQuery("*:*");

		String solrUrl = getSolrUrl(indexName);
		HttpSolrServer solr = new HttpSolrServer(solrUrl);
		SolrDocumentList results = solr.query(query).getResults();
		long totalRecords = results.getNumFound();

		query.setRows(ROWS_PER_FILE);
		query.set("wt", "csv");
		query.set("fl", "*");
		for (int i = 0; i < totalRecords; i+= ROWS_PER_FILE) {
			query.setStart(i);
			URL url = new URL(solrUrl + "/select?" + query.toString());
			File file = new File(targetDir.getCanonicalPath(), makeExportFilename(totalRecords, i));
			if (file.createNewFile()) {
				FileUtils.copyURLToFile(url, file);
			} // TODO complain otherwise
		}
	}

	private static String makeExportFilename(long totalRecords, int index) {
		return "export_" + StringUtils.leftPad("" + index, (int) Math.log(totalRecords / ROWS_PER_FILE) + 1, "0") + ".csv";
	}

	private static String getSolrUrl(String indexName) {
		// TODO do this properly
		return "http://localhost:8080/solr/" + indexName;
	}

	private static void printHelpAndExit(Options options, int exitCode)
	{
		HelpFormatter myhelp = new HelpFormatter();
		myhelp.printHelp(ReindexSolr.class.getSimpleName() + "\n", options);
		System.exit(exitCode);
	}
}
