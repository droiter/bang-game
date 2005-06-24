//
// $Id$

package com.threerings.bang.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import java.util.HashMap;
import java.util.Iterator;

import com.jme.bui.BComponent;
import com.jme.bui.event.MouseEvent;
import com.jme.bui.event.MouseMotionListener;
import com.jme.bui.event.MouseWheelListener;
import com.jme.image.Image;
import com.jme.image.Texture;
import com.jme.intersection.TrianglePickResults;
import com.jme.math.Ray;
import com.jme.math.Vector2f;
import com.jme.math.Vector3f;
import com.jme.renderer.ColorRGBA;
import com.jme.renderer.Renderer;
import com.jme.scene.Node;
import com.jme.scene.Spatial;
import com.jme.scene.shape.Quad;
import com.jme.scene.state.AlphaState;
import com.jme.scene.state.LightState;
import com.jme.scene.state.RenderState;
import com.jme.scene.state.TextureState;
import com.jme.util.TextureManager;

import com.threerings.jme.input.GodViewHandler;
import com.threerings.jme.sprite.Sprite;

import com.threerings.presents.dobj.EntryAddedEvent;
import com.threerings.presents.dobj.EntryRemovedEvent;
import com.threerings.presents.dobj.EntryUpdatedEvent;
import com.threerings.presents.dobj.SetListener;

import com.threerings.bang.client.sprite.PieceSprite;
import com.threerings.bang.client.sprite.UnitSprite;
import com.threerings.bang.data.BangBoard;
import com.threerings.bang.data.BangConfig;
import com.threerings.bang.data.BangObject;
import com.threerings.bang.data.piece.Piece;
import com.threerings.bang.util.BangContext;
import com.threerings.bang.util.PointSet;
import com.threerings.bang.util.RenderUtil;

import static com.threerings.bang.Log.log;
import static com.threerings.bang.client.BangMetrics.*;

/**
 * Provides a shared base for displaying the board that is extended to
 * create the actual game view as well as the level editor.
 */
public class BoardView extends BComponent
    implements MouseMotionListener, MouseWheelListener
{
    public BoardView (BangContext ctx)
    {
        _ctx = ctx;

        // configure ourselves as the default event target
        _ctx.getInputDispatcher().pushDefaultEventTarget(this);

        // we don't actually want to render in orthographic mode
        _node.setRenderQueueMode(Renderer.QUEUE_INHERIT);

        // create a sky box
        _node.attachChild(new SkyNode(ctx));

        // create some fake ground
        Texture texture = TextureManager.loadTexture(
            getClass().getClassLoader().getResource(
                "rsrc/media/textures/scrub.jpg"),
            Texture.MM_LINEAR, Texture.FM_NEAREST, Image.GUESS_FORMAT_NO_S3TC,
            1.0f, true);
        TextureState tstate =
            ctx.getDisplay().getRenderer().createTextureState();
        tstate.setEnabled(true);
        tstate.setTexture(texture);
        int twid = texture.getImage().getWidth()/4;
        int thei = texture.getImage().getHeight()/4;

        int gsize = 2000, gx = gsize/twid, gy = gsize/thei;
        for (int yy = -gy/2; yy < gy/2; yy++) {
            for (int xx = -gx/2; xx < gx/2; xx++) {
                Quad ground = new Quad("ground", twid, thei);
                _node.attachChild(ground);
                ground.setLightCombineMode(LightState.OFF);
                ground.setLocalTranslation(
                    new Vector3f(xx*twid + twid/2, yy*thei + thei/2, 0f));
                ground.setRenderState(tstate);
                ground.updateRenderState();
            }
        }

        // we'll hang the board geometry off this node
        Node bnode = new Node("board");
        _node.attachChild(bnode);
        bnode.attachChild(_tnode = new Node("tiles"));

        // the children of this node will display highlighted tiles
        bnode.attachChild(_hnode = new Node("highlights"));
        _hnode.setRenderQueueMode(Renderer.QUEUE_TRANSPARENT);

        // we'll hang all of our pieces off this node
        _node.attachChild(_pnode = new Node("pieces"));

        // create our highlight alpha state
        _hastate = ctx.getDisplay().getRenderer().createAlphaState();
        _hastate.setBlendEnabled(true);
        _hastate.setSrcFunction(AlphaState.SB_SRC_ALPHA);
        _hastate.setDstFunction(AlphaState.DB_ONE);
        _hastate.setEnabled(true);

        // this is used to target tiles when deploying a card
        _tgtstate = RenderUtil.createTexture(
            ctx, ctx.loadImage("media/textures/ustatus/crosshairs.png"));
    }

    /**
     * Called when we're to be removed from the display.
     */
    public void shutdown ()
    {
        // clear ourselves as a default event target
        _ctx.getInputDispatcher().popDefaultEventTarget(this);
    }

    /**
     * Sets up the board view with all the necessary bits. This is called
     * by the controller when we enter an already started game or the game
     * in which we're involved gets started.
     */
    public void startGame (BangObject bangobj, BangConfig cfg, int pidx)
    {
        // clear out old piece sprites from a previous game
        for (Iterator<PieceSprite> iter = _pieces.values().iterator();
             iter.hasNext(); ) {
            removeSprite(iter.next());
            iter.remove();
        }

        // add the listener that will react to pertinent events
        _bangobj = bangobj;
        _bangobj.addListener(_blistener);

        // freshen up
        refreshBoard();
    }

    /**
     * Called by the editor when the entire board has changed.
     */
    public void refreshBoard ()
    {
        // remove all the board tile geometry
        _tnode.detachAllChildren();

        // remove any old sprites
        for (PieceSprite sprite : _pieces.values()) {
            removeSprite(sprite);
        }
        _pieces.clear();

        // start afresh
        _board = _bangobj.board;
        _board.shadowPieces(_bangobj.pieces.iterator());
        _bbounds = new Rectangle(0, 0, _board.getWidth(), _board.getHeight());

        // create the board tiles
        for (int yy = 0; yy < _board.getHeight(); yy++) {
            for (int xx = 0; xx < _board.getWidth(); xx++) {
                float bx = xx * TILE_SIZE, by = yy * TILE_SIZE;
                Quad t = new Quad("tile", TILE_SIZE, TILE_SIZE);
                _tnode.attachChild(t);
                t.setLocalTranslation(
                    new Vector3f(bx + TILE_SIZE/2, by + TILE_SIZE/2, 0f));
                TextureState tstate = RenderUtil.getGroundTexture(
                    _board.getTile(xx, yy));
                if (tstate != null) {
                    t.setRenderState(tstate);
                } else {
                    t.setSolidColor(ColorRGBA.black);
                }
            }
        }
        _tnode.setLightCombineMode(LightState.OFF);
        _tnode.updateRenderState();
        _tnode.updateGeometricState(0, true);

        // create sprites for all of the pieces
        for (Iterator iter = _bangobj.pieces.iterator(); iter.hasNext(); ) {
            // this will trigger the creation, initialization and whatnot
            pieceUpdated(null, (Piece)iter.next());
        }

        // if the board is smaller than we are, center it in our view
        centerBoard();
    }

    /**
     * Called by the controller when the round has ended.
     */
    public void endRound ()
    {
        // remove our event listener
        _bangobj.removeListener(_blistener);

        createMarquee("Round over!");
    }

    /**
     * Called by the controller when our game has ended.
     */
    public void endGame ()
    {
        // remove our event listener
        _bangobj.removeListener(_blistener);

        // create a giant game over label and render it
        StringBuffer winners = new StringBuffer();
        for (int ii = 0; ii < _bangobj.winners.length; ii++) {
            if (_bangobj.winners[ii]) {
                if (winners.length() > 0) {
                    winners.append(", ");
                }
                winners.append(_bangobj.players[ii]);
            }
        }
        String wtext = "Game Over!\n";
        if (winners.length() > 1) {
            wtext += "Winner: " + winners;
        } else if (winners.length() > 0) {
            wtext += "Winner: " + winners;
        } else {
            wtext += "No winner!";
        }
        createMarquee(wtext);
    }

    /**
     * Returns the node to which our pieces and piece effects are
     * attached.
     */
    public Node getPieceNode ()
    {
        return _pnode;
    }

    /**
     * Adds a sprite to the active view.
     */
    public void addSprite (Sprite sprite)
    {
        _pnode.attachChild(sprite);
        sprite.updateRenderState();
        sprite.updateGeometricState(0.0f, true);
    }

    /**
     * Removes a sprite from the active view.
     */
    public void removeSprite (Sprite sprite)
    {
        _pnode.detachChild(sprite);
    }

    /**
     * Returns true if the specified sprite is part of the active view.
     */
    public boolean isManaged (PieceSprite sprite)
    {
        return _pnode.hasChild(sprite);
    }

    /**
     * Returns the sprite that the mouse is "hovering" over (the one
     * nearest to the camera that is hit by the ray projecting from the
     * camera to the ground plane at the current mouse coordinates).
     */
    public Sprite getHoverSprite ()
    {
        Vector3f camloc = _ctx.getCamera().getLocation();
        _pick.clear();
        _pnode.findPick(new Ray(camloc, _worldMouse), _pick);
        float dist = Float.MAX_VALUE;
        Sprite hit = null;
        for (int ii = 0; ii < _pick.getNumber(); ii++) {
            Sprite s = getSprite(_pick.getPickData(ii).getTargetMesh());
            if (!isHoverable(s)) {
                continue;
            }
            float sdist = camloc.distanceSquared(s.getWorldTranslation());
            if (sdist < dist) {
                hit = s;
                dist = sdist;
            }
        }
        return hit;
    }

    /**
     * Called when a piece is updated in the game object, though sometimes
     * not immediately but after various effects have been visualized.
     */
    public void pieceUpdated (Piece opiece, Piece npiece)
    {
        if (npiece != null) {
            getPieceSprite(npiece).updated(
                _bangobj.board, npiece, _bangobj.tick);
        }
    }        

    // documentation inherited from interface MouseMotionListener
    public void mouseMoved (MouseEvent e)
    {
        // determine which tile the mouse is over
        Vector2f screenPos = new Vector2f(e.getX(), e.getY());
        _worldMouse = _ctx.getDisplay().getWorldCoordinates(screenPos, 0);
        _worldMouse.subtractLocal(_ctx.getCamera().getLocation());

        // determine which tile the mouse is over
        float dist = -1f * _groundNormal.dot(_ctx.getCamera().getLocation()) /
            _groundNormal.dot(_worldMouse);
        Vector3f ground = _ctx.getCamera().getLocation().add(
            _worldMouse.mult(dist));
        ground.z = 0.1f;

        int mx = (int)Math.floor(ground.x / TILE_SIZE);
        int my = (int)Math.floor(ground.y / TILE_SIZE);
        ground.x = (float)mx *TILE_SIZE + TILE_SIZE/2;
        ground.y = (float)my * TILE_SIZE + TILE_SIZE/2;
//         _cursor.setLocalTranslation(ground);

        // update the sprite over which the mouse is hovering
        Sprite hover = getHoverSprite();
        if (hover != _hover) {
            if (_hover instanceof UnitSprite) {
                ((UnitSprite)_hover).setHovered(false);
            }
            _hover = hover;
            if (_hover instanceof UnitSprite) {
                ((UnitSprite)_hover).setHovered(true);
            }
        }

        if (mx != _mouse.x || my != _mouse.y) {
            _mouse.x = mx;
            _mouse.y = my;
            hoverTileChanged(_mouse.x, _mouse.y);
        }
    }

    // documentation inherited from interface MouseMotionListener
    public void mouseDragged (MouseEvent e)
    {
        // first update our mousely business
        mouseMoved(e);
    }

    // documentation inherited from interface MouseWheelListener
    public void mouseWheeled (MouseEvent e)
    {
        GodViewHandler ih = (GodViewHandler)_ctx.getInputHandler();
        float zoom = ih.getZoomLevel();
        if (e.getDelta() > 0) {
            zoom = Math.max(0f, zoom - 0.1f);
        } else {
            zoom = Math.min(1f, zoom + 0.1f);
        }
        ih.setZoomLevel(zoom);
    }

    // documentation inherited
    protected void wasRemoved ()
    {
        super.wasRemoved();

//         if (_marquee != null) {
//             removeSprite(_marquee);
//             _marquee = null;
//         }
    }

    /** Helper function for {@link #getHoverSprite}. */
    protected boolean isHoverable (Sprite sprite)
    {
        return (sprite != null);
    }

    /** Relocates our virtual view to center the board iff it is smaller
     * than the viewport. */
    protected void centerBoard ()
    {
//         int width = _board.getWidth() * SQUARE,
//             height = _board.getHeight() * SQUARE;
//         int nx = _vbounds.x, ny = _vbounds.y;
//         if (width < _vbounds.width) {
//             nx = (width - _vbounds.width) / 2;
//         }
//         if (height < _vbounds.height) {
//             ny = (height - _vbounds.height) / 2;
//         }
//         setViewLocation(nx, ny);
    }

    /**
     * Creates a big animation that scrolls up the middle of the board.
     */
    protected void createMarquee (String text)
    {
//         Label label = new Label(text, Color.white, getFont().deriveFont(40f));
//         label.setAlignment(Label.CENTER);
//         label.setStyle(Label.OUTLINE);
//         label.setAlternateColor(Color.black);
//         label.setTargetWidth(300);
//         label.layout(this);

//         _marquee = new LabelSprite(label);
//         _marquee.setRenderOrder(100);
//         _marquee.setLocation(
//             _vbounds.x+(_vbounds.width-label.getSize().width)/2,
//             _vbounds.y+(_vbounds.height-label.getSize().height)/2);
//         addSprite(_marquee);
    }

    /**
     * Recolors a board tile based on the (presumably updated) underlying
     * terrain value.
     */
    protected void refreshTile (int tx, int ty)
    {
        Quad t = (Quad)_tnode.getChild(ty * _board.getHeight() + tx);
        TextureState tstate =
            RenderUtil.getGroundTexture(_board.getTile(tx, ty));
        if (tstate != null) {
            t.setRenderState(tstate);
        } else {
            t.clearRenderState(RenderState.RS_TEXTURE);
            t.setSolidColor(ColorRGBA.black);
        }
        t.updateRenderState();
    }

    /**
     * Returns (creating if necessary) the piece sprite associated with
     * the supplied piece. A newly created sprite will automatically be
     * initialized with the supplied piece and added to the board view.
     */
    protected PieceSprite getPieceSprite (Piece piece)
    {
        PieceSprite sprite = _pieces.get(piece.pieceId);
        if (sprite == null) {
            sprite = piece.createSprite();
            sprite.init(_ctx, piece, _bangobj.tick);
            _pieces.put((int)piece.pieceId, sprite);
            addSprite(sprite);
        }
        return sprite;
    }

    /**
     * Removes the sprite associated with the specified piece.
     */
    protected void removePieceSprite (int pieceId)
    {
        PieceSprite sprite = _pieces.remove(pieceId);
        if (sprite != null) {
            removeSprite(sprite);
        } else {
            log.warning("No sprite for removed piece [id=" + pieceId + "].");
        }
    }

    /**
     * This is called when the mouse is moved to hover over a different
     * board tile.
     */
    protected void hoverTileChanged (int tx, int ty)
    {
    }

    /** Creates geometry to highlight the supplied set of tiles. */
    protected void highlightTiles (PointSet set, boolean forFlyer)
    {
        for (int ii = 0, ll = set.size(); ii < ll; ii++) {
            int sx = set.getX(ii), sy = set.getY(ii);
            float size = TILE_SIZE - TILE_SIZE/10;
            Quad quad = new Quad("highlight", size, size);
            quad.setSolidColor(_hcolor);
            quad.setLightCombineMode(LightState.OFF);
            quad.setRenderState(_hastate);
            int elev = forFlyer ? 2 : 0;
            quad.setLocalTranslation(
                new Vector3f(sx * TILE_SIZE + TILE_SIZE/2,
                             sy * TILE_SIZE + TILE_SIZE/2,
                             elev * TILE_SIZE + 0.1f));
            quad.setRenderState(RenderUtil.lequalZBuf);
            quad.updateRenderState();
            _hnode.attachChild(quad);
        }
    }

    /** Creates geometry to "target" the supplied set of tiles. */
    protected void targetTiles (PointSet set)
    {
        for (int ii = 0, ll = set.size(); ii < ll; ii++) {
            int sx = set.getX(ii), sy = set.getY(ii);
            Quad quad = RenderUtil.createIcon(_tgtstate);
            quad.setLocalTranslation(
                new Vector3f(sx * TILE_SIZE + TILE_SIZE/2,
                             sy * TILE_SIZE + TILE_SIZE/2,
                             _bangobj.board.getElevation(sx, sy) * TILE_SIZE));
            quad.setRenderState(RenderUtil.lequalZBuf);
            _hnode.attachChild(quad);
        }
    }

    /** Clears out all highlighted tiles. */
    protected void clearHighlights ()
    {
        _hnode.detachAllChildren();
        _hnode.updateRenderState();
        _hnode.updateGeometricState(0f, true);
    }

    /**
     * Returns the sprite associated with this spatial or null if the
     * spatial is not part of a sprite.
     */
    protected Sprite getSprite (Spatial spatial)
    {
        if (spatial instanceof Sprite) {
            return (Sprite)spatial;
        } else if (spatial.getParent() != null) {
            return getSprite(spatial.getParent());
        } else {
            return null;
        }
    }

    /** Listens for various different events and does the right thing. */
    protected class BoardEventListener
        implements SetListener
    {
        public void entryAdded (EntryAddedEvent event) {
            if (event.getName().equals(BangObject.PIECES)) {
                pieceUpdated(null, (Piece)event.getEntry());
            }
        }

        public void entryUpdated (EntryUpdatedEvent event) {
            if (event.getName().equals(BangObject.PIECES)) {
                pieceUpdated((Piece)event.getOldEntry(),
                             (Piece)event.getEntry());
            }
        }

        public void entryRemoved (EntryRemovedEvent event) {
            if (event.getName().equals(BangObject.PIECES)) {
                removePieceSprite((Integer)event.getKey());
                pieceUpdated((Piece)event.getOldEntry(), null);
            }
        }
    };

    protected BangContext _ctx;
    protected BangObject _bangobj;
    protected BangBoard _board;
    protected Rectangle _bbounds;
    protected BoardEventListener _blistener = new BoardEventListener();

    protected Node _pnode, _tnode, _hnode;
    protected Vector3f _worldMouse;
    protected TrianglePickResults _pick = new TrianglePickResults();
    protected Sprite _hover;

    /** Used to texture a quad that "targets" a tile. */
    protected TextureState _tgtstate;

    /** Used to texture a quad that highlights a tile. */
    protected ColorRGBA _hcolor = new ColorRGBA(1, 1, 0, 0.5f);
    protected AlphaState _hastate;

    /** The current tile coordinates of the mouse. */
    protected Point _mouse = new Point(-1, -1);

    protected HashMap<Integer,PieceSprite> _pieces =
        new HashMap<Integer,PieceSprite>();

    /** Used when intersecting the ground. */
    protected static final Vector3f _groundNormal = new Vector3f(0, 0, 1);

    protected static final ColorRGBA DARK =
        new ColorRGBA(0.3f, 0.3f, 0.3f, 1.0f);
    protected static final ColorRGBA LIGHT =
        new ColorRGBA(0.9f, 0.9f, 0.9f, 1.0f);
    protected static final ColorRGBA BROWN =
        new ColorRGBA(204/255f, 153/255f, 51/255f, 1.0f);
}
