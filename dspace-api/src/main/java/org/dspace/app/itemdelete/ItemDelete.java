/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.itemdelete;


// TODO: we are not likely to need any of these io functions
/*import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.mail.MessagingException;
*/

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
//import org.dspace.content.DCValue;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.ItemIterator;
import org.dspace.content.MetadataSchema;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.I18nUtil;
import org.dspace.core.LogManager;
import org.dspace.core.Utils;
import org.dspace.core.Email;
import org.dspace.eperson.EPerson;
import org.dspace.handle.HandleManager;

/**
 * Item Deleter, to eradicate your DSpace content.
 * For instructions on use, see printUsage() method.
 * <P>
 * ItemDelete deletes any DSpace ojbect with the provided handle.
 * Deleting container objects such as Collections or Communities will
 * result in the deletion of entire object hierarchies contained within.
 * PROCEDE WITH CAUTION, THIS IS A SHARP TOOL.
 *
 * @author Hardy Pottinger
 */
public class ItemDelete
{
     /** log4j logger */
     private static Logger log = Logger.getLogger(ItemExport.class);

    /*
	 *
	 */
    public static void main(String[] argv) throws Exception
    {
        // create an options object and populate it
        CommandLineParser parser = new PosixParser();

        Options options = new Options();

        options.addOption("t", "type", true, "type: COMMUNITY, COLLECTION or ITEM");
        options.addOption("i", "id", true, "ID or handle of thing to delete");
        options.addOption("h", "help", false, "help");

        CommandLine line = parser.parse(options, argv);

        String typeString = null;
        String destDirName = null;
        String myIDString = null;
        int seqStart = -1;
        int myType = -1;

        Item myItem = null;
        Collection mycollection = null;

        if (line.hasOption('h'))
        {
            HelpFormatter myhelp = new HelpFormatter();
            myhelp.printHelp("ItemDelete\n", options);
            System.out
                    .println("\nfull community: ItemDelete -t COMMUNITY -i ID");
             System.out
                    .println("\nfull collection: ItemDelete -t COLLECTION -i ID");
            System.out
                    .println("singleitem:       ItemDelete -t ITEM -i ID");

            System.exit(0);
        }

        if (line.hasOption('t')) // type
        {
            typeString = line.getOptionValue('t');

            if ("ITEM".equals(typeString))
            {
                myType = Constants.ITEM;
            }
            else if ("COLLECTION".equals(typeString))
            {
                myType = Constants.COLLECTION;
            }
            else if ("COMMUNITY".equals(typeString))
            {
                myType = Constants.COMMUNITY;
            }
         }

        if (line.hasOption('i')) // id
        {
            myIDString = line.getOptionValue('i');
        }

        // now validate the args
        if (myType == -1)
        {
            System.out
                    .println("type must be COMMUNITY, COLLECTION or ITEM (-h for help)");
            System.exit(1);
        }

        if (myIDString == null)
        {
            System.out
                    .println("ID must be set to either a database ID or a handle (-h for help)");
            System.exit(1);
        }

        Context c = new Context();
        c.setIgnoreAuthorization(true);

        if (myType == Constants.ITEM)
        {
            // first, is myIDString a handle?
            if (myIDString.indexOf('/') != -1)
            {
                myItem = (Item) HandleManager.resolveToObject(c, myIDString);

                if ((myItem == null) || (myItem.getType() != Constants.ITEM))
                {
                    myItem = null;
                }
            }
            else
            {
                myItem = Item.find(c, Integer.parseInt(myIDString));
            }

            if (myItem == null)
            {
                System.out
                        .println("Error, item cannot be found: " + myIDString);
            }
        }
        else
        {
            if (myIDString.indexOf('/') != -1)
            {
                // has a / must be a handle
                mycollection = (Collection) HandleManager.resolveToObject(c,
                        myIDString);

                // ensure it's a collection
                if ((mycollection == null)
                        || (mycollection.getType() != Constants.COLLECTION))
                {
                    mycollection = null;
                }
            }
            else if (myIDString != null)
            {
                mycollection = Collection.find(c, Integer.parseInt(myIDString));
            }

            if (mycollection == null)
            {
                System.out.println("Error, collection cannot be found: "
                        + myIDString);
                System.exit(1);
            }
        }

       if (myItem != null)
        {
            // it's only a single item
            exportItem(c, myItem, destDirName, seqStart, migrate);
        }
        else // it's either a collection or community
            //TODO: use a switch here, too many ifs
        {
            System.out.println("Deleting collection: " + myIDString);

            // it's a collection, so do a bunch of items
 
            // This is whacked, blast out this iterator, just delete stuff 
            ItemIterator i = mycollection.getItems();
            try
            {
                exportItem(c, i, destDirName, seqStart, migrate);
            }
            finally
            {
                if (i != null)
                {
                    i.close();
                }
            }
        }

        c.complete();
    }

    private static void exportItem(Context c, ItemIterator i,
            String destDirName, int seqStart, boolean migrate) throws Exception
    {
        int mySequenceNumber = seqStart;
        int counter = SUBDIR_LIMIT - 1;
        int subDirSuffix = 0;
        String fullPath = destDirName;
        String subdir = "";
        File dir;

        if (SUBDIR_LIMIT > 0)
        {
            dir = new File(destDirName);
            if (!dir.isDirectory())
            {
                throw new IOException(destDirName + " is not a directory.");
            }
        }

        System.out.println("Beginning export");

        while (i.hasNext())
        {
            if (SUBDIR_LIMIT > 0 && ++counter == SUBDIR_LIMIT)
            {
                subdir = Integer.valueOf(subDirSuffix++).toString();
                fullPath = destDirName + File.separatorChar + subdir;
                counter = 0;

                if (!new File(fullPath).mkdirs())
                {
                    throw new IOException("Error, can't make dir " + fullPath);
                }
            }

            System.out.println("Exporting item to " + mySequenceNumber);
            exportItem(c, i.next(), fullPath, mySequenceNumber, migrate);
            mySequenceNumber++;
        }
    }

    private static void exportItem(Context c, Item myItem, String destDirName,
            int seqStart, boolean migrate) throws Exception
    {
        File destDir = new File(destDirName);

        if (destDir.exists())
        {
            // now create a subdirectory
            File itemDir = new File(destDir + "/" + seqStart);

            System.out.println("Exporting Item " + myItem.getID() + " to "
                    + itemDir);

            if (itemDir.exists())
            {
                throw new Exception("Directory " + destDir + "/" + seqStart
                        + " already exists!");
            }

            if (itemDir.mkdir())
            {
                // make it this far, now start exporting
                writeMetadata(c, myItem, itemDir, migrate);
                writeBitstreams(c, myItem, itemDir);
                if (!migrate)
                {
                    writeHandle(c, myItem, itemDir);
                }
            }
            else
            {
                throw new Exception("Error, can't make dir " + itemDir);
            }
        }
        else
        {
            throw new Exception("Error, directory " + destDirName
                    + " doesn't exist!");
        }
    }

    /**
     * Discover the different schemas in use and output a separate metadata XML
     * file for each schema.
     *
     * @param c
     * @param i
     * @param destDir
     * @throws Exception
     */
    private static void writeMetadata(Context c, Item i, File destDir, boolean migrate)
            throws Exception
    {
        Set<String> schemas = new HashSet<String>();
        DCValue[] dcValues = i.getMetadata(Item.ANY, Item.ANY, Item.ANY, Item.ANY);
        for (DCValue dcValue : dcValues)
        {
            schemas.add(dcValue.schema);
        }

        // Save each of the schemas into it's own metadata file
        for (String schema : schemas)
        {
            writeMetadata(c, schema, i, destDir, migrate);
        }
    }

    // output the item's dublin core into the item directory
    private static void writeMetadata(Context c, String schema, Item i,
            File destDir, boolean migrate) throws Exception
    {
        String filename;
        if (schema.equals(MetadataSchema.DC_SCHEMA))
        {
            filename = "dublin_core.xml";
        }
        else
        {
            filename = "metadata_" + schema + ".xml";
        }

        File outFile = new File(destDir, filename);

        System.out.println("Attempting to create file " + outFile);

        if (outFile.createNewFile())
        {
            BufferedOutputStream out = new BufferedOutputStream(
                    new FileOutputStream(outFile));

            DCValue[] dcorevalues = i.getMetadata(schema, Item.ANY, Item.ANY,
                    Item.ANY);

            // XML preamble
            byte[] utf8 = "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>\n"
                    .getBytes("UTF-8");
            out.write(utf8, 0, utf8.length);

            String dcTag = "<dublin_core schema=\"" + schema + "\">\n";
            utf8 = dcTag.getBytes("UTF-8");
            out.write(utf8, 0, utf8.length);

            String dateIssued = null;
            String dateAccessioned = null;

            for (DCValue dcv : dcorevalues)
            {
                String qualifier = dcv.qualifier;

                if (qualifier == null)
                {
                    qualifier = "none";
                }

                String language = dcv.language;

                if (language != null)
                {
                    language = " language=\"" + language + "\"";
                }
                else
                {
                    language = "";
                }

                utf8 = ("  <dcvalue element=\"" + dcv.element + "\" "
                        + "qualifier=\"" + qualifier + "\""
                        + language + ">"
                        + Utils.addEntities(dcv.value) + "</dcvalue>\n")
                        .getBytes("UTF-8");

                if ((!migrate) ||
                    (migrate && !(
                     ("date".equals(dcv.element) && "issued".equals(qualifier)) ||
                     ("date".equals(dcv.element) && "accessioned".equals(qualifier)) ||
                     ("date".equals(dcv.element) && "available".equals(qualifier)) ||
                     ("identifier".equals(dcv.element) && "uri".equals(qualifier) &&
                      (dcv.value != null && dcv.value.startsWith("http://hdl.handle.net/" +
                       HandleManager.getPrefix() + "/"))) ||
                     ("description".equals(dcv.element) && "provenance".equals(qualifier)) ||
                     ("format".equals(dcv.element) && "extent".equals(qualifier)) ||
                     ("format".equals(dcv.element) && "mimetype".equals(qualifier)))))
                {
                    out.write(utf8, 0, utf8.length);
                }

                // Store the date issued and accession to see if they are different
                // because we need to keep date.issued if they are, when migrating
                if (("date".equals(dcv.element) && "issued".equals(qualifier)))
                {
                    dateIssued = dcv.value;
                }
                if (("date".equals(dcv.element) && "accessioned".equals(qualifier)))
                {
                    dateAccessioned = dcv.value;
                }
            }

            // When migrating, only keep date.issued if it is different to date.accessioned
            if ((migrate) &&
                (dateIssued != null) &&
                (dateAccessioned != null) &&
                (!dateIssued.equals(dateAccessioned)))
            {
                utf8 = ("  <dcvalue element=\"date\" "
                        + "qualifier=\"issued\">"
                        + Utils.addEntities(dateIssued) + "</dcvalue>\n")
                        .getBytes("UTF-8");
                out.write(utf8, 0, utf8.length);
            }

            utf8 = "</dublin_core>\n".getBytes("UTF-8");
            out.write(utf8, 0, utf8.length);

            out.close();
        }
        else
        {
            throw new Exception("Cannot create dublin_core.xml in " + destDir);
        }
    }

    // create the file 'handle' which contains the handle assigned to the item
    private static void writeHandle(Context c, Item i, File destDir)
            throws Exception
    {
        if (i.getHandle() == null)
        {
            return;
        }
        String filename = "handle";

        File outFile = new File(destDir, filename);

        if (outFile.createNewFile())
        {
            PrintWriter out = new PrintWriter(new FileWriter(outFile));

            out.println(i.getHandle());

            // close the contents file
            out.close();
        }
        else
        {
            throw new Exception("Cannot create file " + filename + " in "
                    + destDir);
        }
    }

}
