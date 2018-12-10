/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

 /*
THE PLAN:
[x] get the project metadata from the Project Master Item
what fields are we going to use for the links we will be checking below?
we talked about using dc.relation.ispartof and dc.relation.requires, but that's not expressive enough for what we need
we need four new fields: vsim.relation.community, vsim.relation.models, vsim.relation.archives, vsim.relation.submissions
ALL/some of these links *can* be added to the dc fields, too, but that's not really important to us right now.
We need to add them to fields we can use to also recall the values in this script
*/

// TODO: make this whole thing Idempotent (see below for notes, around line 109)

package org.dspace.ctask.general;

import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang.StringUtils;

import org.apache.commons.io.FilenameUtils;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.MetadataSchema;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.core.Constants;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;

import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.Group;
import org.dspace.eperson.service.GroupService;

import org.apache.log4j.Logger;

import org.dspace.services.factory.DSpaceServicesFactory;

import java.io.File;

import org.dspace.content.MetadataValue;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.*;

import java.io.InputStream;
import java.io.FileInputStream;

import java.sql.SQLException;
import java.io.IOException;

/**
 * VSimProjectFixCommunityMetadataCurationTask is a task that fixes a VSim Project Community metadata by adding a link to the VSim Project Master item
 *
 * @author hardyoyo
 */

@Distributive
public class VSimProjectFixCommunityMetadataCurationTask extends AbstractCurationTask
{
/** log4j category */
    private static final Logger log = Logger.getLogger(VSimProjectFixCommunityMetadataCurationTask.class);

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

    /**
     * Perform the curation task upon passed DSO
     *
     * @param dso the DSpace object
     * @throws IOException if IO error
     * @throws SQLException if SQL error
     */

     @Override
     protected void performObject(DSpaceObject dso) throws IOException
     {

    int status = Curator.CURATE_SKIP;

    // read some configuration settings
    //reference: ConfigurationService info: https://wiki.duraspace.org/display/DSPACE/DSpace+Spring+Services+Tutorial#DSpaceSpringServicesTutorial-DSpaceConfigurationService
    String projectMasterCollectionHandle = DSpaceServicesFactory.getInstance().getConfigurationService().getProperty("vsim.project.master.collection.handle");
    String assetstoreDir = DSpaceServicesFactory.getInstance().getConfigurationService().getProperty("assetstore.dir");

    // if the projectMasterCollectionHandle value isn't set, use a default
    if (StringUtils.isEmpty(projectMasterCollectionHandle))
      {
        projectMasterCollectionHandle = "20.500.11991/1009"; // <-- that better be a collection object on that handle
      }


    vsimInit:

          try {

            switch (dso.getType()) {
              case Constants.ITEM:
                Item item = (Item) dso;
                DSpaceObject projectMastersDSO = handleService.resolveToObject(Curator.curationContext(), projectMasterCollectionHandle);
                Collection projectMastersCollection = (Collection) projectMastersDSO;

                // *ONLY* KEEP GOING IF THIS ITEM IS A PROJECT MASTER, OTHERWISE *STOP*!!

                  if (!itemService.isIn(item, projectMastersCollection)) {
                      break vsimInit;
                  }


                    String itemId = item.getHandle();
                    log.info("VSimProjectCurationTask: processing master item at handle: " + itemId);

                    // grab the projectCommunity based on the vsim.relation.community handle in the projectMaster item
                    List<MetadataValue> mvVsimRelationCommunity = itemService.getMetadata(item, "vsim", "relation", "community", Item.ANY);
                    String projectCommunityHandle = mvVsimRelationCommunity.get(0).getValue();
                    Community projectCommunity = (Community) handleService.resolveToObject(Curator.curationContext(), projectCommunityHandle);

                    // ADD A LINK TO BACK TO THE PROJECT MASTER ITEM
                    communityService.addMetadata(Curator.curationContext(), projectCommunity, "vsim", "relation", "projectMaster", null, itemId);

                    // update of the projectCommunity metadata (AKA: write!)
                    communityService.update(Curator.curationContext(), projectCommunity);


                    // set the success flag and add a line to the result report
                    // KEEP THIS AT THE END OF THE SCRIPT

                    status = Curator.CURATE_SUCCESS;
                    result = "VSim Project community metadata fixed based on " + itemId + " | Project Community: " + projectCommunityHandle;
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


          setResult(result);
          report(result);

    }

}
