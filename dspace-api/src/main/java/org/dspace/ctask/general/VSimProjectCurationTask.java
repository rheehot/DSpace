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

package org.dspace.ctask.general;

import java.util.List;

import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.MetadataSchema;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.core.Constants;
import org.dspace.curate.Curator;

import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.*;

import java.io.IOException;

public class VSimProjectCurationTask extends AbstractCurationTask
{
    protected CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
    protected int status = Curator.CURATE_UNSET;
    protected String result = null;

    /**
     * Perform the curation task upon passed DSO
     *
     * @param dso the DSpace object
     * @throws IOException if IO error
     */

    @Override
    public int perform(DSpaceObject dso) throws IOException
    {

    int status = Curator.CURATE_SKIP;

		if (dso.getType() == Constants.ITEM)
        {
            // Get All requried MetadataValues, all are returned as lists, use .get(0).getValue() to return the first value, like strings,
            // use the usual list stuff to manage multiple values
            Item item = (Item)dso;
            String itemId = item.getHandle();
            List<MetadataValue> mvDcTitle = itemService.getMetadata(item, "dc", "title", Item.ANY, Item.ANY);
            List<MetadataValue> mvDcDescriptionAbstract = itemService.getMetadata(item, "dc", "description", "abstract", Item.ANY);
            List<MetadataValue> mvDcDescription = itemService.getMetadata(item, "dc", "description", Item.ANY, Item.ANY);
            List<MetadataValue> mvDcRelation = itemService.getMetadata(item, "dc", "relation", Item.ANY, Item.ANY);
            List<MetadataValue> mvDcDateCreated = itemService.getMetadata(item, "dc", "date", "created", Item.ANY);
            List<MetadataValue> mvDcDescriptionSponsorship = itemService.getMetadata(item, "dc", "description", "sponsorship", Item.ANY);
            List<MetadataValue> mvDcCoverageSpatial = itemService.getMetadata(item, "dc", "coverage", "spatial", Item.ANY);
            List<MetadataValue> mvDcCoverageTemporal = itemService.getMetadata(item, "dc", "coverage", "temporal", Item.ANY);
            List<MetadataValue> mvDcContributorAuthor = itemService.getMetadata(item, "dc", "", "contributor", "author", Item.ANY);
            List<MetadataValue> mvDcContributor = itemService.getMetadata(item, "dc", "", "contributor", Item.ANY, Item.ANY);
            List<MetadataValue> mvDcContributorAdvisor = itemService.getMetadata(item, "dc", "", "contributor", "advisor", Item.ANY);
            List<MetadataValue> mvDcDescriptionURI = itemService.getMetadata(item, "dc", "description", "uri", Item.ANY);
            List<MetadataValue> mvDcDateAvailable = itemService.getMetadata(item, "dc", "date", "available", Item.ANY);
            List<MetadataValue> mvDcRightsHolder = itemService.getMetadata(item, "dc", "rights", "holder", Item.ANY);
            List<MetadataValue> mvDcDateCopyright = itemService.getMetadata(item, "dc", "date", "copyright", Item.ANY);
            List<MetadataValue> mvDcRights = itemService.getMetadata(item, "dc", "rights", Item.ANY, Item.ANY);
            List<MetadataValue> mvDcDateIssued = itemService.getMetadata(item, "dc", "date", "issued", Item.ANY);
            List<MetadataValue> mvVsimResearchObjective = itemService.getMetadata(item, "vsim", "research", "objective", Item.ANY);
            List<MetadataValue> mvVsimAcknowledgements = itemService.getMetadata(item, "vsim", "acknowledgements", Item.ANY, Item.ANY);
            List<MetadataValue> mvVsimBibliography = itemService.getMetadata(item, "vsim", "bibliography", Item.ANY, Item.ANY);
            List<MetadataValue> mvVsimKeywords = itemService.getMetadata(item, "vsim", "keywords", Item.ANY, Item.ANY);
            List<MetadataValue> mvVsimContributorDetails = itemService.getMetadata(item, "vsim", "contributor", "details", Item.ANY);
            List<MetadataValue> mvVsimNews = itemService.getMetadata(item, "vsim", "news", Item.ANY, Item.ANY);

            /* these don't exist yet
            List<MetadataValue> mvVsimRelationCommunity = itemService.getMetadata(item, "vsim", "relation", "community", Item.ANY);
            List<MetadataValue> mvVsimRelationModels = itemService.getMetadata(item, "vsim", "relation", "models", Item.ANY);
            List<MetadataValue> mvVsimRelationArchives = itemService.getMetadata(item, "vsim", "relation", "archives", Item.ANY);
            List<MetadataValue> mvVsimRelationSubmissions = itemService.getMetadata(item, "vsim", "relation", "submissions", Item.ANY);
            */


            // TODO: (only do this if there is no link to the project community in this projectMaster item's metadata)

            // create a top level community for this project
            Community projectCommunity = CommunityService.create(null, Curator.curationContext());

            // set what metadata we can on this new community
            // example here: https://github.com/DSpace/DSpace/blob/ea642d6c9289d96b37b5de3bb7a4863ec48eaa9c/dspace-api/src/test/java/org/dspace/content/packager/PackageUtilsTest.java#L79-L80

            // set the title (dc.title)
            CommunityService.addMetadata(context, projectCommunity, MetadataSchema.DC_SCHEMA, "title", null, null, mvDcTitle.get(0).getValue());

            // set the description (dc.description)
            CommunityService.addMetadata(context, projectCommunity, MetadataSchema.DC_SCHEMA, "description", null, null, mvDcDescription.get(0).getValue());

            // set the short_description (dc.description.abstract)
            CommunityService.addMetadata(context, projectCommunity, MetadataSchema.DC_SCHEMA, "description", "abstract", null, mvDcDescriptionAbstract.get(0).getValue());

            // TODO: set the sidebar_text (dc.description.tableofcontents) we'll have to interpolate this from other values, requires discussion and/or thought
            // probably it'll be a link to the project master? leave blank for now

            // set the copyright_text (dc.rights)
            CommunityService.addMetadata(context, projectCommunity, MetadataSchema.DC_SCHEMA, "rights", null, null, mvDcRights.get(0).getValue());

            // finish the update of the projectCommunity metadata (AKA: write!)
            CommunityService.update(context, projectCommunity);

            // snag the projectCommunityhandle, we'll need it
            String projectCommunityHandle = topCommunity.getHandle();



            // TODO: set the logo for the community, if possible, use projectCommunity.setLogo(Bitstream logo)
            // TODO: before we can do that, we need to find the Bitstream logo on this Project master item
            // TODO: set the admins for this community, use setAdmins(Group admins)


            // TODO add a link to the top level community as metadata for this project master Item (use vsim.relation.community)


            // TODO: (if there is no link to the project models collection in this item's metadata) create a models collection in this project's TLC and add a link to the models collection as metadata for this project master item

            // TODO: (if there is no link to the project archives collection in this item's metadata) create an archives collection in this project's TLC and add a link to the archives collection as metadata for this project master item

            // TODO: (if there is no link to the project submissions collection in this item's metadata) create a submissions collection in this project's TLC and add a link to the submissions collection as metadata for this project master item



            // set the success flag and add a line to the result report
            // KEEP THIS AT THE END OF THE SCRIPT
            context.restoreAuthSystemState();

            status = Curator.CURATE_SUCCESS;
            result = "VSim Project intialized based on " + item.getHandle() + " | title: " + mvDcTitle.get(0).getValue() + " | Project Community: " + projectCommunityHandle;

            setResult(result);
            report(result);
		}

        return status;
    }


}
