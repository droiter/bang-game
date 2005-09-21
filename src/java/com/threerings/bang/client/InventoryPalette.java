//
// $Id$

package com.threerings.bang.client;

import java.util.Iterator;

import com.jmex.bui.BContainer;
import com.jmex.bui.BLabel;
import com.jmex.bui.border.EmptyBorder;
import com.jmex.bui.layout.TableLayout;

import com.threerings.bang.data.BangCodes;
import com.threerings.bang.data.BangUserObject;
import com.threerings.bang.data.Item;
import com.threerings.bang.util.BangContext;

/**
 * Displays the user's inventory.
 */
public class InventoryPalette extends BContainer
{
    public InventoryPalette (BangContext ctx)
    {
        super(new TableLayout(3, 5, 5));
        setBorder(new EmptyBorder(5, 5, 5, 5));

        int added = 0;
        BangUserObject user = ctx.getUserObject();
        for (Iterator iter = user.inventory.iterator(); iter.hasNext(); ) {
            Item item = (Item)iter.next();
            ItemIcon icon = item.createIcon();
            if (icon == null) {
                continue;
            }
            icon.setItem(ctx, item);
            add(icon);
            added++;
        }

        if (added == 0) {
            String msg = ctx.xlate(BangCodes.BANG_MSGS, "m.status_noinventory");
            add(new BLabel(msg));
        }
    }
}
