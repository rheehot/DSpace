/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.discovery;

import org.apache.cocoon.environment.Request;
import org.apache.commons.lang.StringUtils;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverFilterQuery;
import org.dspace.discovery.SearchService;
import org.dspace.utils.DSpace;

import java.util.*;

/**
 * @author Kevin Van de Velde (kevin at atmire dot com)
 * @author Ben Bosman (ben at atmire dot com)
 * @author Mark Diggory (markd at atmire dot com)
 */
public class DiscoveryUIUtils {

    private static SearchService searchService = null;

    static {
        DSpace dspace = new DSpace();
        searchService = dspace.getServiceManager().getServiceByName(SearchService.class.getName(),SearchService.class);
    }



    /**
     * Returns a list of the filter queries for use in rendering pages, creating page more urls, ....
     * @return an array containing the filter queries
     */
    public static Map<String, String[]> getParameterFilterQueries(Request request) {
        Map<String, String[]> fqs = new HashMap<String, String[]>();

        List<String> filterTypes = getRepeatableParameters(request, "filtertype");
        List<String> filterOperators = getRepeatableParameters(request, "filter_relational_operator");
        List<String> filterValues = getRepeatableParameters(request, "filter");

        for (int i = 0; i < filterTypes.size(); i++) {
            String filterType = filterTypes.get(i);
            String filterValue = filterValues.get(i);
            String filterOperator = filterOperators.get(i);

            fqs.put("filtertype_" + i, new String[]{filterType});
            fqs.put("filter_relational_operator_" + i, new String[]{filterOperator});
            fqs.put("filter_" + i, new String[]{filterValue});
        }
        return fqs;
    }

    /**
     * Returns all the filter queries for use by discovery
     * @return an array containing the filter queries
     */
    public static String[] getFilterQueries(Request request, Context context) {
        List<String> result = new ArrayList<>();
        Collection<DiscoverFilterQuery> discoveryQueries = getFilterDiscoveryQueries(request, context);

        for(DiscoverFilterQuery filterQuery : discoveryQueries){
            result.add(filterQuery.getFilterQuery());
        }
        return result.toArray(new String[result.size()]);
    }

    public static Collection<DiscoverFilterQuery> getFilterDiscoveryQueries(Request request, Context context) {
        try {
            List<DiscoverFilterQuery> allFilterQueries = new ArrayList<>();
            List<String> filterTypes = getRepeatableParameters(request, "filtertype");
            List<String> filterOperators = getRepeatableParameters(request, "filter_relational_operator");
            List<String> filterValues = getRepeatableParameters(request, "filter");

            for (int i = 0; i < filterTypes.size(); i++) {
                String filterType = filterTypes.get(i);
                String filterOperator = filterOperators.get(i);
                String filterValue = filterValues.get(i);

                if(StringUtils.isNotBlank(filterValue)){
                    allFilterQueries.add(searchService.toFilterQuery(context, (filterType.equals("*") ? "" : filterType), filterOperator, filterValue));
                }
            }

            return allFilterQueries;
        }
        catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static List<String> getRepeatableParameters(Request request, String prefix){
        TreeMap<String, String> result = new TreeMap<String, String>();

        Enumeration parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String parameter = (String) parameterNames.nextElement();
            if(parameter.startsWith(prefix)){
                result.put(parameter, request.getParameter(parameter));
            }
        }
        return new ArrayList<String>(result.values());
    }
}
