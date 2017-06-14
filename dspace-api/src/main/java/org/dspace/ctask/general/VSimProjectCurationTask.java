/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

 /*
THE PLAN:
TODO: get the project metadata from the Project Master Item
TODO: (if there is no link to the project community in this item's metadata) create a Project top level community for this project and add a link to the top level community as metadata for this project master Item
TODO: (if there is no link to the project models collection in this item's metadata) create a models collection in this project's TLC and add a link to the models collection as metadata for this project master item
TODO: (if there is no link to the project archives collection in this item's metadata) create an archives collection in this project's TLC and add a link to the archives collection as metadata for this project master item
TODO: (if there is no link to the project submissions collection in this item's metadata) create a submissions collection in this project's TLC and add a link to the submissions collection as metadata for this project master item
 */

package org.dspace.ctask.general;

import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;

import java.io.IOException;

public class VSimProjectCurationTask extends AbstractCurationTask
{

    protected int status = Curator.CURATE_UNSET;
    protected String result = null;

    @Override
    public int perform(DSpaceObject dso) throws IOException
    {

		if (dso instanceof Item)
        {
            Item item = (Item)dso;
            status = Curator.CURATE_SUCCESS;
            result = "VSim Project intialized based on " + item.getHandle();

            setResult(result);
            report(result);
		}

        return status;
    }


}
