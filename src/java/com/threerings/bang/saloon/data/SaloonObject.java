//
// $Id$

package com.threerings.bang.saloon.data;

import com.threerings.crowd.data.PlaceObject;

/**
 * Contains distributed data for the Saloon.
 */
public class SaloonObject extends PlaceObject
{
    // AUTO-GENERATED: FIELDS START
    /** The field name of the <code>service</code> field. */
    public static final String SERVICE = "service";
    // AUTO-GENERATED: FIELDS END

    /** The means by which the client makes requests to the server. */
    public SaloonMarshaller service;

    // AUTO-GENERATED: METHODS START
    /**
     * Requests that the <code>service</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setService (SaloonMarshaller value)
    {
        SaloonMarshaller ovalue = this.service;
        requestAttributeChange(
            SERVICE, value, ovalue);
        this.service = value;
    }
    // AUTO-GENERATED: METHODS END
}
