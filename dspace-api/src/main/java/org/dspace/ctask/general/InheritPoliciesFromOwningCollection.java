/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.general;

import java.io.IOException;
import java.sql.SQLException;

import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.Collection;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;
import java.util.ArrayList;
import java.util.List;

/**
 * InheritPoliciesFromOwningCollection is a task that sets the access policies for item DSOs to the default polices defined by their owning collection
 *
 * @author hardyoyo
 */
@Distributive
public class InheritPoliciesFromOwningCollection extends AbstractCurationTask
{
	private List<String> results;


    /**
     * Perform the curation task upon passed DSO
     *
     * @param dso the DSpace object
     * @throws IOException
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException
    {
        distribute(dso);
		formatResults();
	    return Curator.CURATE_SUCCESS;
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

		private void addResult(Item item, String status, String message) {
			results.add(item.getHandle() + " (" + status + ") " + message);
		}

		private void formatResults() {
			StringBuilder outputResult = new StringBuilder();
			for(String result : results) {
				outputResult.append(result).append("\n");
			}
			setResult(outputResult.toString());
		}

}
