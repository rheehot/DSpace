/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.itemdelete;

/**
 * An exception that can be thrown when error occur during item deletion
 */
public class ItemDeleteException extends Exception
{
    //TODO: Add any common errors here
    public static final int EXPORT_TOO_LARGE = 0;

    private int reason;

    public ItemDeleteException(int r, String message)
    {
        super(message);
        reason = r;
    }

    public int getReason()
    {
        return reason;
    }
}
