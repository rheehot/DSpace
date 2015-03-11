package org.dspace.util;

import org.apache.commons.cli.*;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.dspace.core.ConfigurationManager;
import org.joda.time.*;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import java.io.IOException;
import java.net.ConnectException;
import java.util.*;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for the LCoNZ Institutional Research Repositories
 */
public class ReindexSolr
{

	public static final String HELP_OPTION = "h";
	public static final String INDEX_NAME_OPTION = "i";

	public static void main(String[] args) throws ParseException {
		CommandLineParser parser = new PosixParser();
		Options options = new Options();
		options.addOption(HELP_OPTION, "help", false, "Get help on options for this command.");
		options.addOption(INDEX_NAME_OPTION, "index-name", true, "The names of the indexes to process. At least one is required. Available indexes are: authority, statistics.");

		try
		{
			CommandLine line = parser.parse(options, args);
			if (line.hasOption(HELP_OPTION))
			{
				printHelpAndExit(options, 0);
			}
			if (!line.hasOption(INDEX_NAME_OPTION))
			{
				System.out.println("This command requires the index-name option but none was present.");
				printHelpAndExit(options, 1);
			}
			String[] indexNames = line.getOptionValues(INDEX_NAME_OPTION);
			for (String indexName : indexNames)
			{
				String solrURL = findSolrUrl(indexName);
				if (StringUtils.isBlank(solrURL))
				{
					System.out.println("Solr index name " + indexName + " not recognised or no URL could be found for the index.");
					printHelpAndExit(options, 1);
				}
				String timeField = "statistics".equals(indexName) ? "time" : "last_modified_date";
				try {
					DateTime before = DateTime.now();

					reindex(solrURL, timeField);

					DateTime after = DateTime.now();
					Period period = new Period(before, after);
					PeriodFormatter formatter = new PeriodFormatterBuilder().appendDays().appendSuffix(" days ").appendHours().appendSuffix(" hours ")
							                            .appendMinutes().appendSuffix(" minutes ").appendSeconds().appendSuffix(" seconds")
							                            .toFormatter();
					System.out.println("Processed index " + indexName + " in " + formatter.print(period));
				}
				catch (SolrServerException | ReindexSolrException | IOException e) {
					if (e.getCause() != null && e.getCause() instanceof ConnectException)
					{
						System.err.println("Solr needs to be running for this command to succeed but doesn't appear to be reachable: " + e.getMessage());
					}
					else
					{
						System.err.println("An exception was caught trying to re-index " + indexName + ": " + e.getMessage());
						e.printStackTrace(System.err);
					}
				}
			}
		}
		catch (ParseException e)
		{
			System.err.println("Cannot read command options");
			printHelpAndExit(options, 1);
		}
	}

	private static String findSolrUrl(String indexName)
	{
		if ("authority".equals(indexName))
		{
			return ConfigurationManager.getProperty("solr.authority.server");
		}
		if ("statistics".equals(indexName))
		{
			// TODO allow year cores to be reindexed too?
			return ConfigurationManager.getProperty("solr-statistics", "server");
		}
		return null;
	}

	private static void reindex(String solrURL, String timeField) throws SolrServerException, ReindexSolrException, IOException {
		HttpSolrServer solr = new HttpSolrServer(solrURL);

		Date earliestDate = getEarliestDocDate(timeField, solr);
		if (earliestDate == null) {
			throw new ReindexSolrException("Cannot find earliest document in index");
		}
		int monthsSinceEarliest = calculateMonthsSince(earliestDate);

		String queryString = timeField + ":[NOW/MONTH TO NOW]";
		SolrQuery query = new SolrQuery(queryString);
		query.setRows(250_000);

		for (int monthsAgo = 1; monthsAgo <= monthsSinceEarliest + 1; monthsAgo++)
		{
			SolrDocumentList results = solr.query(query).getResults();
			System.out.println("This month has " + results.getNumFound() + " documents");

			Iterator<SolrDocument> iterator = results.iterator();

			List<SolrInputDocument> addBack = new ArrayList<>();
			while (iterator.hasNext())
			{
				SolrDocument result = iterator.next();
				addBack.add(ClientUtils.toSolrInputDocument(result));
			}

			if (!addBack.isEmpty())
			{
				solr.add(addBack);
				solr.commit(true, true);
				System.out.println("Processed " + addBack.size() + " documents");
			}

			// TODO do the same thing again if this month has more than 250000 docs

			// prepare query for next round
			queryString = timeField + ":[" + "NOW/MONTH-" + monthsAgo + "MONTHS"
					              + " TO "
					              + "NOW/MONTH" + (monthsAgo > 1 ? "-" + (monthsAgo - 1) + "MONTHS" : "") + "]";
			query.setQuery(queryString);
		}
	}

	private static int calculateMonthsSince(Date from)
	{
		DateTime now = DateTime.now(DateTimeZone.forTimeZone(TimeZone.getTimeZone("UTC"))).withTimeAtStartOfDay();
		DateTime actualFrom = new DateTime(from).withTimeAtStartOfDay();

		return Months.monthsBetween(actualFrom, now).getMonths();
	}

	private static Date getEarliestDocDate(String timeField, HttpSolrServer solr) throws SolrServerException
	{
		SolrQuery query = new SolrQuery("*:*");
		query.setRows(1);
		query.setSort(timeField, SolrQuery.ORDER.asc);
		query.setFields(timeField);

		QueryResponse response = solr.query(query);
		SolrDocumentList results = response.getResults();
		if (results.size() != 1)
		{
			return null;
		}
		return (Date) results.get(0).getFieldValue(timeField);
	}

	private static void printHelpAndExit(Options options, int exitCode)
	{
		HelpFormatter myhelp = new HelpFormatter();
		myhelp.printHelp(ReindexSolr.class.getSimpleName() + "\n", options);
		System.exit(exitCode);
	}

	private static class ReindexSolrException extends Throwable
	{
		public ReindexSolrException(String message)
		{
			super(message);
		}
	}
}
