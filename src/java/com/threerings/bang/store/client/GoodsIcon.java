//
// $Id$

package com.threerings.bang.store.client;

import com.jme.renderer.Renderer;
import com.jmex.bui.Label;
import com.jmex.bui.icon.ImageIcon;
import com.jmex.bui.util.Dimension;
import com.jmex.bui.util.Insets;

import com.threerings.media.image.ColorPository;
import com.threerings.media.image.Colorization;

import com.threerings.bang.client.BangUI;
import com.threerings.bang.client.bui.SelectableIcon;
import com.threerings.bang.data.BangCodes;
import com.threerings.bang.util.BangContext;

import com.threerings.bang.avatar.util.AvatarLogic;

import com.threerings.bang.store.data.ArticleGood;
import com.threerings.bang.store.data.Good;

/**
 * Displays a salable good.
 */
public class GoodsIcon extends SelectableIcon
{
    public static final Dimension ICON_SIZE = new Dimension(136, 156);

    /** Contains our randomly selected color ids for colorized goods. */
    public int[] colorIds;

    public GoodsIcon (BangContext ctx, Good good)
    {
        _ctx = ctx;
        _text = new Label(this);
        setGood(good);
    }

    public Good getGood ()
    {
        return _good;
    }

    public void setGood (Good good)
    {
        _good = good;

        if (_good instanceof ArticleGood) {
            AvatarLogic al = _ctx.getAvatarLogic();
            String[] cclasses = al.getColorizationClasses(
                al.getArticleCatalog().getArticle(_good.getType()));
            colorIds = new int[3];
            Colorization[] zations = new Colorization[cclasses.length];
            for (int ii = 0; ii < zations.length; ii++) {
                ColorPository.ColorRecord crec =
                    al.getColorPository().getRandomStartingColor(cclasses[ii]);
                if (crec == null) {
                    continue;
                }
                int cidx = AvatarLogic.getColorIndex(crec.cclass.name);
                colorIds[cidx] = crec.colorId;
                zations[ii] = crec.getColorization();
            }
            setIcon(new ImageIcon(
                        _ctx.getImageCache().createImage(
                            _ctx.getImageCache().getBufferedImage(
                                good.getIconPath()),
                            zations, true)));
        } else {
            setIcon(new ImageIcon(_ctx.loadImage(good.getIconPath())));
        }

        _text.setText(_ctx.xlate(BangCodes.GOODS_MSGS, good.getName()));
    }

    public Dimension getPreferredSize (int whint, int hhint)
    {
        return ICON_SIZE;
    }

    // documentation inherited
    protected void wasAdded ()
    {
        super.wasAdded();
        _text.stateDidChange();
    }

    // documentation inherited
    protected void stateDidChange ()
    {
        super.stateDidChange();
        _text.stateDidChange();
    }

    // documentation inherited
    protected void layout ()
    {
        super.layout();

        // we need to do some jiggery pokery to force the label in a bit from
        // the edges
        Insets insets = new Insets(getInsets());
        insets.left += 5;
        insets.top += 10;
        insets.right += 5;
        _text.layout(insets);
    }

    // documentation inherited
    protected void renderComponent (Renderer renderer)
    {
        super.renderComponent(renderer);
        _text.render(renderer);
    }

    protected BangContext _ctx;
    protected Good _good;
    protected Label _text;
}
