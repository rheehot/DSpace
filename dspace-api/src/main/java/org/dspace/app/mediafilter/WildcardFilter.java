/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.mediafilter;

import org.dspace.content.Item;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.swing.text.Document;

import java.io.ByteArrayOutputStream;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;

/*
 *
 *
 */
public class WildcardFilter extends MediaFilter
{

    @Override
    public String getFilteredName(String oldFilename)
    {
        return oldFilename + ".txt";
    }

    /**
     * @return String bundle name
     *
     */
    @Override
    public String getBundleName()
    {
        return "TEXT";
    }

    /**
     * @return String bitstreamformat
     */
    @Override
    public String getFormatString()
    {
        return "Text";
    }

    /**
     * @return String description
     */
    @Override
    public String getDescription()
    {
        return "Extracted text";
    }

    public File inputStreamToTempFile(InputStream source, String prefix, String suffix) throws IOException {
            File f = File.createTempFile(prefix, suffix);
            f.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(f);

            byte[] buffer = new byte[1024];
            int len = source.read(buffer);
            while (len != -1) {
                    fos.write(buffer, 0, len);
                    len = source.read(buffer);
            }
            fos.close();
            return f;
    }

    public String execToString(String command) throws Exception {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      CommandLine commandline = CommandLine.parse(command);
      DefaultExecutor exec = new DefaultExecutor();
      PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
      exec.setStreamHandler(streamHandler);
      exec.execute(commandline);
      return(outputStream.toString());
    }

    /**
     * @param currentItem item
     * @param source source input stream
     * @param verbose verbose mode
     *
     * @return InputStream the resulting input stream
     * @throws Exception if error
     */
    @Override
    public InputStream getDestinationStream(Item currentItem, InputStream source, boolean verbose)
            throws Exception
    {
        String extractedText = "";
        // VSIM-33 run the juice.sh script to pull the content of this binary file out

        // make a temp file so we don't destroy our bitstream by accident
        File f = inputStreamToTempFile(source, "wildcardFilter", ".tmp");
          try
          {
            String juiceCommand = "/usr/local/vsim/sentences/juice.sh " + f.getAbsolutePath();
            extractedText = execToString(juiceCommand);
          }
          finally
          {
            // clean up our temp files
            f.delete();
          }

        // generate an input stream with the extracted text
        byte[] textBytes = extractedText.getBytes();
        ByteArrayInputStream bais = new ByteArrayInputStream(textBytes);

        return bais; // will this work? or will the byte array be out of scope?
    }
}
