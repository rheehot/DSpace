/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.metadata;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import org.dspace.authorize.AuthorizeManager;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.ItemIterator;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;

/**
 * Report on exceptional items within a collection or a community *
 * @author twb27
 */
@Distributive
public class ExceptionReport extends AbstractCurationTask
{
    private enum DGExceptionItem{
        PrivateItem("Is Private Item"),
        WithdrawnItem("Is Withdrawn Item"),
        RestrictedAccessItem("Has Restricted Access"),
        RestrictedAccessBitstream("Has Original Bitstream with Restricted Access"),
        NoOriginalBitstream("Has No Original Bitstreams"),
        MultipleOriginalBitstreams("Has Multiple Original Bitstreams"),
        UnusualBitstreamType("Has Unexpected Bitstream Types");
        
        String desc = "";
        int count = 0;
        ArrayList<String> exceptionTable = new ArrayList<String>();
        static int max = 25;
        
        public void reset() {
            count = 0;
            exceptionTable.clear();
        }
        
        DGExceptionItem(String desc) {
            this.desc = desc;
        }
        
        public void addHandle(String handle) {
            count++;
            if (count <= max) exceptionTable.add(handle);
        }
        
        public String header() {
            StringBuilder sb = new StringBuilder(desc);
            sb.append(String.format(" (%d", count));
            if (count > max) sb.append(String.format(", %d showing", max));
            sb.append(")");
            return sb.toString();
        }
    }
    
    private int totalItems;

    private static String[] standardMimes = {"application/pdf", "image/jpeg"};
    /**
     * Test item type against the standard mime types to be contained in this repository
     * @return true if the mime type is in the standard list 
     */
    public boolean isStandardMimeType(String type) {
        for(String s: standardMimes) {
            if (type.equals(s)) return true;
        }
        return false;
    }

    /**
     * Perform the curation task upon passed DSO
     *
     * @param dso the DSpace object
     * @throws IOException
     * @throws SQLException 
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException
    {
        try {
            totalItems = 0;
            for(DGExceptionItem dgei: DGExceptionItem.values()) {
                dgei.reset();
            }
            if (dso == null) {
                Community[] topComm = Community.findAllTop(Curator.curationContext());
                for (Community comm : topComm)
                {
                    distribute(comm);
                }                
            } else {
                distribute(dso);                
            }
            formatResults();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Curator.CURATE_SUCCESS;
    }
    
    @Override
    public int perform(Context ctx, String id) throws IOException
    {
        DSpaceObject dso = dereference(ctx, id);
        return perform(dso);
    }
    /**
     * Distributes a task through a DSpace container - a convenience method
     * for tasks declaring the <code>@Distributive</code> property. 
     * <P>
     * This method invokes the 'performObject()' method on the current DSO, and
     * then recursively invokes the 'performObject()' method on all DSOs contained
     * within the current DSO. For example: if a Community is passed in, then
     * 'performObject()' will be called on that Community object, as well as 
     * on all SubCommunities/Collections/Items contained in that Community.
     * <P>
     * Individual tasks MUST override either the <code>performObject</code> method or
     * the <code>performItem</code> method to ensure the task is run on either all
     * DSOs or just all Items, respectively.
     * 
     * @param dso current DSpaceObject
     * @throws IOException
     */
    protected void distribute(DSpaceObject dso) throws IOException
    {
        try
        {
            //perform task on this current object
            performObject(dso);
            
            //next, we'll try to distribute to all child objects, based on container type
            int type = dso.getType();
            if (Constants.COLLECTION == type)
            {
                ItemIterator iter = ((Collection)dso).getAllItems();
                while (iter.hasNext())
                {
                    performObject(iter.next());
                }
                iter.close();
            }
            else if (Constants.COMMUNITY == type)
            {
                Community comm = (Community)dso;
                for (Community subcomm : comm.getSubcommunities())
                {
                    distribute(subcomm);
                }
                for (Collection coll : comm.getCollections())
                {
                    distribute(coll);
                }
            }
            else if (Constants.SITE == type)
            {
                Community[] topComm = Community.findAllTop(Curator.curationContext());
                for (Community comm : topComm)
                {
                    distribute(comm);
                }
            }
        }
        catch (SQLException sqlE)
        {
            throw new IOException(sqlE.getMessage(), sqlE);
        }       
    }


    @Override
    protected void performItem(Item item) throws SQLException, IOException
    {   

        // get the owning collection
        Collection owningColl = item.getOwningCollection();

        // inherit the policies for this item from its owning collection
        try {
            item.inheritCollectionDefaultPolicies(owningColl);
            addResult(item, "success", "Inherited policies from owning collection" );
            return;

        } catch (Exception e) {

            addResult(item, "error", "Unable to inherit policies from owning collection" );
            return; 
        } 
    }   



/*
    @Override
    protected void performItem(Item item) throws SQLException, IOException
    {
        totalItems++;
        if (item.isWithdrawn()) {
            DGExceptionItem.WithdrawnItem.addHandle(item.getHandle());
        }
        
        if (!item.isDiscoverable()) {
            DGExceptionItem.PrivateItem.addHandle(item.getHandle());
        }
        
        if (!AuthorizeManager.authorizeActionBoolean(Curator.curationContext(), item, Constants.READ, false)) {
            DGExceptionItem.RestrictedAccessItem.addHandle(item.getHandle());
        }
        
        int count = 0;
        int unsuppType = 0;
        String errtype = "";
        
        boolean hasAnon = true;
        for (Bundle bundle : item.getBundles("ORIGINAL")) {
            for (Bitstream bs : bundle.getBitstreams()) {
                count++;
                String type = bs.getFormat().getMIMEType();
                
                if (isStandardMimeType(type)) {
                } else {
                    errtype = type;
                    unsuppType++;
                }
                
                hasAnon = hasAnon && AuthorizeManager.authorizeActionBoolean(Curator.curationContext(), bs, Constants.READ, false);
            }           
        }
        
        if (count == 0) {
            DGExceptionItem.NoOriginalBitstream.addHandle(item.getHandle());            
        } else if (count > 1) {
            DGExceptionItem.MultipleOriginalBitstreams.addHandle(item.getHandle());
        }

        if (!hasAnon) {
            DGExceptionItem.RestrictedAccessBitstream.addHandle(item.getHandle());
        }
        if (unsuppType > 0) {
            DGExceptionItem.UnusualBitstreamType.addHandle(item.getHandle() + " " + errtype);
        }
        
        if (totalItems % 1000 == 0) {
            System.out.println(String.format("%6d items processed", totalItems));
            Curator.curationContext().clearCache();
        }
    }
*/
    
    private void formatResults() throws IOException {
        try {
            Context c = new Context();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("\n\nTOTAL ITEMS (%d)\n",totalItems));
            for(DGExceptionItem dgei: DGExceptionItem.values()) {
                sb.append(dgei.header());
                sb.append("\n");
                for(String val: dgei.exceptionTable) {
                    sb.append("\t");
                    sb.append(val);
                    sb.append("\n");
                }
            }
            report(sb.toString());
            setResult(sb.toString());
            c.complete();
        }
        catch (SQLException sqlE)
        {
            throw new IOException(sqlE.getMessage(), sqlE);
        }
    }
}
