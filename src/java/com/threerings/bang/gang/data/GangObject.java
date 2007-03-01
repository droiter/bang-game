//
// $Id$

package com.threerings.bang.gang.data;

import java.net.URL;

import com.samskivert.util.ArrayUtil;

import com.threerings.presents.dobj.DObject;
import com.threerings.presents.dobj.DSet;

import com.threerings.crowd.chat.data.SpeakMarshaller;
import com.threerings.crowd.chat.data.SpeakObject;

import com.threerings.bang.data.AvatarInfo;
import com.threerings.bang.data.BuckleInfo;
import com.threerings.bang.data.BucklePart;
import com.threerings.bang.data.Handle;
import com.threerings.bang.data.Item;
import com.threerings.bang.saloon.data.TopRankObject;
import com.threerings.bang.saloon.data.TopRankedList;

import com.threerings.bang.gang.util.GangUtil;

import static com.threerings.bang.Log.log;

/**
 * Contains data concerning a single gang.
 */
public class GangObject extends DObject
    implements SpeakObject, TopRankObject
{
    // AUTO-GENERATED: FIELDS START
    /** The field name of the <code>gangPeerService</code> field. */
    public static final String GANG_PEER_SERVICE = "gangPeerService";

    /** The field name of the <code>speakService</code> field. */
    public static final String SPEAK_SERVICE = "speakService";

    /** The field name of the <code>gangId</code> field. */
    public static final String GANG_ID = "gangId";

    /** The field name of the <code>name</code> field. */
    public static final String NAME = "name";

    /** The field name of the <code>founded</code> field. */
    public static final String FOUNDED = "founded";

    /** The field name of the <code>statement</code> field. */
    public static final String STATEMENT = "statement";

    /** The field name of the <code>url</code> field. */
    public static final String URL = "url";

    /** The field name of the <code>avatar</code> field. */
    public static final String AVATAR = "avatar";

    /** The field name of the <code>scrip</code> field. */
    public static final String SCRIP = "scrip";

    /** The field name of the <code>coins</code> field. */
    public static final String COINS = "coins";

    /** The field name of the <code>notoriety</code> field. */
    public static final String NOTORIETY = "notoriety";

    /** The field name of the <code>notorietyRank</code> field. */
    public static final String NOTORIETY_RANK = "notorietyRank";

    /** The field name of the <code>buckle</code> field. */
    public static final String BUCKLE = "buckle";

    /** The field name of the <code>outfit</code> field. */
    public static final String OUTFIT = "outfit";

    /** The field name of the <code>inventory</code> field. */
    public static final String INVENTORY = "inventory";

    /** The field name of the <code>members</code> field. */
    public static final String MEMBERS = "members";

    /** The field name of the <code>topRanked</code> field. */
    public static final String TOP_RANKED = "topRanked";
    // AUTO-GENERATED: FIELDS END

    /** Used by peers to make requests on the behalf of their users. */
    public GangPeerMarshaller gangPeerService;

    /** The service used to send chat messages.  This is rewritten for peer nodes. */
    public SpeakMarshaller speakService;

    /** This gang's unique identifier. */
    public int gangId;

    /** The name of this gang. */
    public Handle name;

    /** The day on which this gang was founded. */
    public long founded;

    /** The gang's statement. */
    public String statement;

    /** The gang's URL. */
    public String url;

    /** The gang leader's avatar. */
    public AvatarInfo avatar;

    /** The amount of scrip in the gang's coffers. */
    public int scrip;

    /** The number of coins in the gang's coffers. */
    public int coins;

    /** The gang's total notoriety. */
    public int notoriety;

    /** The gang's rank in terms of notoriety. */
    public byte notorietyRank;

    /** The ids of the items comprising the currently configured buckle. */
    public int[] buckle;

    /** The currently configured gang outfit. */
    public OutfitArticle[] outfit;

    /** Contains all items held by the gang. */
    public DSet<Item> inventory;

    /** Contains a {@link GangMemberEntry} for each member of this gang. */
    public DSet<GangMemberEntry> members = new DSet<GangMemberEntry>();

    /** Contains info on the top-ranked members by various criterion. */
    public DSet<TopRankedList> topRanked = new DSet<TopRankedList>();

    /** On servers using this object as a proxy, the oid on the peer server. */
    public transient int remoteOid;

    /** On servers, stores the town index for the benefit of {@link #applyToListeners}. */
    public transient int townIdx;

    /**
     * Returns the name used to identity the gang's entry in the coin database.
     */
    public String getCoinAccount ()
    {
        return "{" + name + "}";
    }

    /**
     * Returns the URL of the gang's home page, or <code>null</code> if no valid URL has been
     * configured.
     */
    public URL getURL ()
    {
        try {
            return new URL(url);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Using the buckle parts in the inventory and the part list in the {@link #buckle} field,
     * composes and returns the buckle fingerprint.
     */
    public BuckleInfo getBuckle ()
    {
        // find the listed parts
        BucklePart[] parts = new BucklePart[buckle.length];
        for (int ii = 0; ii < parts.length; ii++) {
            Item item = inventory.get(buckle[ii]);
            if (!(item instanceof BucklePart)) {
                log.warning("Invalid part in buckle [gang=" + name + ", item=" + item + "].");
                return null;
            }
            parts[ii] = (BucklePart)item;
        }

        // put them into the print in order, with each component having an encoded component id
        // and colorization followed by encoded coordinates
        int[] print = new int[0], pair = new int[2];
        for (BucklePart part : parts) {
            pair[1] = (part.getX() << 16) | (part.getY() & 0xFFFF);
            for (int comp : part.getComponents()) {
                pair[0] = comp;
                print = ArrayUtil.concatenate(print, pair);
            }
        }
        return new BuckleInfo(print);
    }

    /**
     * Returns the {@link GangMemberEntry} corresponding to the most senior active leader of the
     * gang.
     */
    public GangMemberEntry getSeniorLeader ()
    {
        return GangUtil.getSeniorLeader(members);
    }

    // documentation inherited from interface SpeakObject
    public void applyToListeners (SpeakObject.ListenerOp op)
    {
        for (GangMemberEntry member : members) {
            if (member.townIdx == townIdx) {
                op.apply(member.handle);
            }
        }
    }

    // documentation inherited from interface TopRankObject
    public DSet<TopRankedList> getTopRanked ()
    {
        return topRanked;
    }

    // AUTO-GENERATED: METHODS START
    /**
     * Requests that the <code>gangPeerService</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setGangPeerService (GangPeerMarshaller value)
    {
        GangPeerMarshaller ovalue = this.gangPeerService;
        requestAttributeChange(
            GANG_PEER_SERVICE, value, ovalue);
        this.gangPeerService = value;
    }

    /**
     * Requests that the <code>speakService</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setSpeakService (SpeakMarshaller value)
    {
        SpeakMarshaller ovalue = this.speakService;
        requestAttributeChange(
            SPEAK_SERVICE, value, ovalue);
        this.speakService = value;
    }

    /**
     * Requests that the <code>gangId</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setGangId (int value)
    {
        int ovalue = this.gangId;
        requestAttributeChange(
            GANG_ID, Integer.valueOf(value), Integer.valueOf(ovalue));
        this.gangId = value;
    }

    /**
     * Requests that the <code>name</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setName (Handle value)
    {
        Handle ovalue = this.name;
        requestAttributeChange(
            NAME, value, ovalue);
        this.name = value;
    }

    /**
     * Requests that the <code>founded</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setFounded (long value)
    {
        long ovalue = this.founded;
        requestAttributeChange(
            FOUNDED, Long.valueOf(value), Long.valueOf(ovalue));
        this.founded = value;
    }

    /**
     * Requests that the <code>statement</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setStatement (String value)
    {
        String ovalue = this.statement;
        requestAttributeChange(
            STATEMENT, value, ovalue);
        this.statement = value;
    }

    /**
     * Requests that the <code>url</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setUrl (String value)
    {
        String ovalue = this.url;
        requestAttributeChange(
            URL, value, ovalue);
        this.url = value;
    }

    /**
     * Requests that the <code>avatar</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setAvatar (AvatarInfo value)
    {
        AvatarInfo ovalue = this.avatar;
        requestAttributeChange(
            AVATAR, value, ovalue);
        this.avatar = value;
    }

    /**
     * Requests that the <code>scrip</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setScrip (int value)
    {
        int ovalue = this.scrip;
        requestAttributeChange(
            SCRIP, Integer.valueOf(value), Integer.valueOf(ovalue));
        this.scrip = value;
    }

    /**
     * Requests that the <code>coins</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setCoins (int value)
    {
        int ovalue = this.coins;
        requestAttributeChange(
            COINS, Integer.valueOf(value), Integer.valueOf(ovalue));
        this.coins = value;
    }

    /**
     * Requests that the <code>notoriety</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setNotoriety (int value)
    {
        int ovalue = this.notoriety;
        requestAttributeChange(
            NOTORIETY, Integer.valueOf(value), Integer.valueOf(ovalue));
        this.notoriety = value;
    }

    /**
     * Requests that the <code>notorietyRank</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setNotorietyRank (byte value)
    {
        byte ovalue = this.notorietyRank;
        requestAttributeChange(
            NOTORIETY_RANK, Byte.valueOf(value), Byte.valueOf(ovalue));
        this.notorietyRank = value;
    }

    /**
     * Requests that the <code>buckle</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setBuckle (int[] value)
    {
        int[] ovalue = this.buckle;
        requestAttributeChange(
            BUCKLE, value, ovalue);
        this.buckle = (value == null) ? null : (int[])value.clone();
    }

    /**
     * Requests that the <code>index</code>th element of
     * <code>buckle</code> field be set to the specified value.
     * The local value will be updated immediately and an event will be
     * propagated through the system to notify all listeners that the
     * attribute did change. Proxied copies of this object (on clients)
     * will apply the value change when they received the attribute
     * changed notification.
     */
    public void setBuckleAt (int value, int index)
    {
        int ovalue = this.buckle[index];
        requestElementUpdate(
            BUCKLE, index, Integer.valueOf(value), Integer.valueOf(ovalue));
        this.buckle[index] = value;
    }

    /**
     * Requests that the <code>outfit</code> field be set to the
     * specified value. The local value will be updated immediately and an
     * event will be propagated through the system to notify all listeners
     * that the attribute did change. Proxied copies of this object (on
     * clients) will apply the value change when they received the
     * attribute changed notification.
     */
    public void setOutfit (OutfitArticle[] value)
    {
        OutfitArticle[] ovalue = this.outfit;
        requestAttributeChange(
            OUTFIT, value, ovalue);
        this.outfit = (value == null) ? null : (OutfitArticle[])value.clone();
    }

    /**
     * Requests that the <code>index</code>th element of
     * <code>outfit</code> field be set to the specified value.
     * The local value will be updated immediately and an event will be
     * propagated through the system to notify all listeners that the
     * attribute did change. Proxied copies of this object (on clients)
     * will apply the value change when they received the attribute
     * changed notification.
     */
    public void setOutfitAt (OutfitArticle value, int index)
    {
        OutfitArticle ovalue = this.outfit[index];
        requestElementUpdate(
            OUTFIT, index, value, ovalue);
        this.outfit[index] = value;
    }

    /**
     * Requests that the specified entry be added to the
     * <code>inventory</code> set. The set will not change until the event is
     * actually propagated through the system.
     */
    public void addToInventory (Item elem)
    {
        requestEntryAdd(INVENTORY, inventory, elem);
    }

    /**
     * Requests that the entry matching the supplied key be removed from
     * the <code>inventory</code> set. The set will not change until the
     * event is actually propagated through the system.
     */
    public void removeFromInventory (Comparable key)
    {
        requestEntryRemove(INVENTORY, inventory, key);
    }

    /**
     * Requests that the specified entry be updated in the
     * <code>inventory</code> set. The set will not change until the event is
     * actually propagated through the system.
     */
    public void updateInventory (Item elem)
    {
        requestEntryUpdate(INVENTORY, inventory, elem);
    }

    /**
     * Requests that the <code>inventory</code> field be set to the
     * specified value. Generally one only adds, updates and removes
     * entries of a distributed set, but certain situations call for a
     * complete replacement of the set value. The local value will be
     * updated immediately and an event will be propagated through the
     * system to notify all listeners that the attribute did
     * change. Proxied copies of this object (on clients) will apply the
     * value change when they received the attribute changed notification.
     */
    public void setInventory (DSet<com.threerings.bang.data.Item> value)
    {
        requestAttributeChange(INVENTORY, value, this.inventory);
        @SuppressWarnings("unchecked") DSet<com.threerings.bang.data.Item> clone =
            (value == null) ? null : value.typedClone();
        this.inventory = clone;
    }

    /**
     * Requests that the specified entry be added to the
     * <code>members</code> set. The set will not change until the event is
     * actually propagated through the system.
     */
    public void addToMembers (GangMemberEntry elem)
    {
        requestEntryAdd(MEMBERS, members, elem);
    }

    /**
     * Requests that the entry matching the supplied key be removed from
     * the <code>members</code> set. The set will not change until the
     * event is actually propagated through the system.
     */
    public void removeFromMembers (Comparable key)
    {
        requestEntryRemove(MEMBERS, members, key);
    }

    /**
     * Requests that the specified entry be updated in the
     * <code>members</code> set. The set will not change until the event is
     * actually propagated through the system.
     */
    public void updateMembers (GangMemberEntry elem)
    {
        requestEntryUpdate(MEMBERS, members, elem);
    }

    /**
     * Requests that the <code>members</code> field be set to the
     * specified value. Generally one only adds, updates and removes
     * entries of a distributed set, but certain situations call for a
     * complete replacement of the set value. The local value will be
     * updated immediately and an event will be propagated through the
     * system to notify all listeners that the attribute did
     * change. Proxied copies of this object (on clients) will apply the
     * value change when they received the attribute changed notification.
     */
    public void setMembers (DSet<com.threerings.bang.gang.data.GangMemberEntry> value)
    {
        requestAttributeChange(MEMBERS, value, this.members);
        @SuppressWarnings("unchecked") DSet<com.threerings.bang.gang.data.GangMemberEntry> clone =
            (value == null) ? null : value.typedClone();
        this.members = clone;
    }

    /**
     * Requests that the specified entry be added to the
     * <code>topRanked</code> set. The set will not change until the event is
     * actually propagated through the system.
     */
    public void addToTopRanked (TopRankedList elem)
    {
        requestEntryAdd(TOP_RANKED, topRanked, elem);
    }

    /**
     * Requests that the entry matching the supplied key be removed from
     * the <code>topRanked</code> set. The set will not change until the
     * event is actually propagated through the system.
     */
    public void removeFromTopRanked (Comparable key)
    {
        requestEntryRemove(TOP_RANKED, topRanked, key);
    }

    /**
     * Requests that the specified entry be updated in the
     * <code>topRanked</code> set. The set will not change until the event is
     * actually propagated through the system.
     */
    public void updateTopRanked (TopRankedList elem)
    {
        requestEntryUpdate(TOP_RANKED, topRanked, elem);
    }

    /**
     * Requests that the <code>topRanked</code> field be set to the
     * specified value. Generally one only adds, updates and removes
     * entries of a distributed set, but certain situations call for a
     * complete replacement of the set value. The local value will be
     * updated immediately and an event will be propagated through the
     * system to notify all listeners that the attribute did
     * change. Proxied copies of this object (on clients) will apply the
     * value change when they received the attribute changed notification.
     */
    public void setTopRanked (DSet<com.threerings.bang.saloon.data.TopRankedList> value)
    {
        requestAttributeChange(TOP_RANKED, value, this.topRanked);
        @SuppressWarnings("unchecked") DSet<com.threerings.bang.saloon.data.TopRankedList> clone =
            (value == null) ? null : value.typedClone();
        this.topRanked = clone;
    }
    // AUTO-GENERATED: METHODS END
}
