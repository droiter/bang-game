//
// $Id$

package com.threerings.bang.client;

import com.jmex.bui.BLabel;
import com.jmex.bui.ImageIcon;
import com.jmex.bui.util.Dimension;

import com.threerings.bang.data.Item;
import com.threerings.bang.util.BangContext;

/**
 * Displays an icon and descriptive text for a particular inventory item.
 */
public class ItemIcon extends BLabel
{
    public ItemIcon ()
    {
        super("");
        setOrientation(VERTICAL);
        setHorizontalAlignment(CENTER);
    }

    public void setItem (BangContext ctx, Item item)
    {
        _item = item;
        configureLabel(ctx);
    }

    @Override // documentation inherited
    public Dimension getPreferredSize ()
    {
        return new Dimension(128, 143);
    }

    protected void configureLabel (BangContext ctx)
    {
        setIcon(new ImageIcon(ctx.loadImage("ui/unknown_item.png")));
        setText(_item.toString());
    }

    protected Item _item;
}
