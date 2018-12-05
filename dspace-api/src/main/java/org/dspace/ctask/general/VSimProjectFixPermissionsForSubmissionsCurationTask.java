/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.general;

import java.util.List;
import org.apache.commons.lang.StringUtils;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.Collection;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.core.Constants;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;

import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.Group;
import org.dspace.eperson.service.GroupService;

import org.apache.log4j.Logger;

import org.dspace.services.factory.DSpaceServicesFactory;

import org.dspace.content.MetadataValue;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.*;

import java.sql.SQLException;
import java.io.IOException;

/**
 * VSimProjectFixPermissionsForSubmissionsCurationTask is a task that ensures that the Contributor group has submit permissions for the Submissions folder of VSim projects
 *
 * @author hardyoyo
 */

@Distributive
public class VSimProjectFixPermissionsForSubmissionsCurationTask extends AbstractCurationTask
{
/** log4j category */
    private static final Logger log = Logger.getLogger(VSimProjectFixPermissionsForSubmissionsCurationTask.class);

    protected CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
    protected CollectionService collectionService = ContentServiceFactory.getInstance().getCollectionService();
    protected HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    protected GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    protected int status = Curator.CURATE_UNSET;
    protected String result = null;

    /**
     * Perform the curation task upon passed DSO
     *
     * @param dso the DSpace object
     * @throws IOException if IO error
     * @throws SQLException if SQL error
     */

     @Override
     public int perform(DSpaceObject dso) throws IOException
     {
          distribute(dso);
          return Curator.CURATE_SUCCESS;
     }

     @Override
     protected void performObject(DSpaceObject dso) throws IOException
     {

    int status = Curator.CURATE_SKIP;

    // set a breakpoint
    vsimInit:

          try {

          // We need a DSpace group object for AuthZ purposes, for the Contributor group, to keep handy
          Group ContributorGroupObj = groupService.findByName(Curator.curationContext(), "Contributor");

          // *ONLY* KEEP GOING IF THIS ITEM IS A Collection, OTHERWISE *STOP*!!
          switch (dso.getType()) {
              case Constants.COLLECTION:
                Collection projectCollSubmissions = (Collection) dso;

                // only work if the collection's name ends with ' User Submissions'
                if ( !projectCollSubmissions.getName().endsWith( " User Submissions" ) ) {
                  break vsimInit;
                }

                // WARNING! DESTRUCTIVE! delete the current submitters group for this collection and replace it with a new one
                collectionService.removeSubmitters(Curator.curationContext(), projectCollSubmissions);
                Group projectCollSubmissionsSubmittersGroupObj = collectionService.createSubmitters(Curator.curationContext(), projectCollSubmissions);

                // add the ContributorGroupObj to the submitter group we just created
                groupService.addMember(Curator.curationContext(), projectCollSubmissionsSubmittersGroupObj, ContributorGroupObj);
                groupService.update(Curator.curationContext(), projectCollSubmissionsSubmittersGroupObj);

                // get the ID and name to this collection, so we can echo them to the logs
                String collectionID = projectCollSubmissions.getID().toString();
                String collectionName = projectCollSubmissions.getName();
                log.info("VSimProjectFixPermissionsForSubmissionsCurationTask: processing collection at handle: " + collectionID + " : " + collectionName);

                // Update the projectCollSubmissions collection, to save the changes we made above
                collectionService.update(Curator.curationContext(), projectCollSubmissions);
                status = Curator.CURATE_SUCCESS;
                break;

                default: status = Curator.CURATE_SUCCESS;
                break;
            }

            // catch any exceptions
            } catch (AuthorizeException authE) {
        		log.error("caught exception: " + authE);
        		status = Curator.CURATE_FAIL;
           	} catch (SQLException sqlE) {
        		log.error("caught exception: " + sqlE);
           	}

            result = "VSimProjectFixPermissionsForSubmissionsCurationTask COMPLETED SUCCESSFULLY!";


              setResult(result);
              report(result);

    }

}
