/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

 /*
THE PLAN:
TODO: get the project metadata from the Project Master Item, to include all of
of the following:

dc.title: string
dc.description.abstract: string
dc.description: string
dc.relation: list
dc.date.created: string?
dc.description.sponsorship: string
dc.coverage.spatial: string
dc.coverage.temporal: string
dc.contributor.author: list
dc.contributor: list
dc.contributor.advisor: list
dc.description.uri: string
dc.date.available: string?
dc.rights.holder: string
dc.date.copyright: string?
dc.rights: string
dc.date.issued: string?
dc.description.uri: string
dc.date.issued (required): string?
vsim.research.objective: string
vsim.acknowledgements: string
vsim.bibliography: string
vsim.keywords: string
vsim.contributor.details: string
vsim.news: string

TODO: (if there is no link to the project community in this item's metadata) create a Project top level community for this project and add a link to the top level community as metadata for this project master Item
TODO: (if there is no link to the project models collection in this item's metadata) create a models collection in this project's TLC and add a link to the models collection as metadata for this project master item
TODO: (if there is no link to the project archives collection in this item's metadata) create an archives collection in this project's TLC and add a link to the archives collection as metadata for this project master item
TODO: (if there is no link to the project submissions collection in this item's metadata) create a submissions collection in this project's TLC and add a link to the submissions collection as metadata for this project master item
 */

package org.dspace.ctask.general;

import java.util.List;

import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.core.Constants;
import org.dspace.curate.Curator;

import org.dspace.content.MetadataValue;

import java.io.IOException;


public class VSimProjectCurationTask extends AbstractCurationTask
{

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

    // Get All requried MetadataValues, all are returned as lists, use .get(0).getValue() to return the first value, like strings,
    // use the usual list stuff to manage multiple values

		if (dso.getType() == Constants.ITEM)
        {
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

            status = Curator.CURATE_SUCCESS;
            result = "VSim Project intialized based on " + item.getHandle() + ". title: " + mvDcTitle.get(0).getValue();

            setResult(result);
            report(result);
		}

        return status;
    }


}
