//
// $Id$

package com.threerings.bang.game.client.sprite;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.jme.math.FastMath;
import com.jme.math.Quaternion;
import com.jme.math.Vector3f;
import com.jme.scene.Node;
import com.jme.scene.Spatial;

import com.threerings.jme.sprite.LinePath;
import com.threerings.jme.sprite.LineSegmentPath;
import com.threerings.jme.sprite.Path;
import com.threerings.jme.sprite.Sprite;
import com.threerings.media.util.MathUtil;
import com.threerings.openal.SoundGroup;

import com.threerings.bang.client.Model;
import com.threerings.bang.util.BasicContext;

import com.threerings.bang.game.client.BoardView;
import com.threerings.bang.game.client.TerrainNode;
import com.threerings.bang.game.data.BangBoard;
import com.threerings.bang.game.data.piece.Piece;

import static com.threerings.bang.Log.log;
import static com.threerings.bang.client.BangMetrics.*;

/**
 * Handles the rendering of a particular piece on the board.
 */
public class PieceSprite extends Sprite
{
    /**
     * Called by the editor to make pieces warp to their new locations for
     * rapid draggability.
     */
    public static void setEditorMode (boolean editorMode)
    {
        _editorMode = editorMode;
    }

    /** Returns the piece associated with this sprite. */
    public Piece getPiece ()
    {
        return _piece;
    }

    /** Returns the id of the piece associated with this sprite. */
    public int getPieceId ()
    {
        return _piece.pieceId;
    }

    /** Indicates to this piece that it is selected by the user. May
     * someday trigger a special "selected" rendering mode, but presently
     * does nothing. */
    public void setSelected (boolean selected)
    {
        if (_selected != selected) {
            _selected = selected;
        }
    }

    /**
     * Returns true if this sprite can be clicked and selected, false if
     * not.
     */
    public boolean isSelectable ()
    {
        return false;
    }

    /**
     * Called when we are first created and immediately before we are
     * added to the display.
     */
    public void init (BasicContext ctx, BoardView view, BangBoard board,
                      SoundGroup sounds, Piece piece, short tick)
    {
        _view = view;
        _piece = piece;
        _tick = tick;

        // create our sprite geometry
        createGeometry(ctx);
        setAnimationSpeed(20);
        setAnimationActive(false);

        // create any sounds associated with this sprite
        createSounds(sounds);

        // position ourselves properly to start
        setLocation(_px = piece.x, _py = piece.y,
                    computeElevation(board, _px, _py));
        setOrientation(_porient = piece.orientation);

        // ensure that we do this the first time even if the sprite starts
        // out at zero zero
        updateCollisionTree();
    }

    /**
     * Allows the piece sprite to return a separate piece of geometry that will
     * be added to the scene along with it as a peer.
     */
    public Spatial getHighlight ()
    {
        return null;
    }

    /**
     * Configures this sprite's tile location.
     */
    public void setLocation (int tx, int ty, int elevation)
    {
        _elevation = elevation;
        toWorldCoords(tx, ty, elevation, _temp);
        if (!_temp.equals(localTranslation)) {
            setLocalTranslation(new Vector3f(_temp));
//             log.info("Moving to " + tx + ", " + ty + ", " + elevation +
//                      ": " + _temp);
            updateCollisionTree();
        }
    }

    /**
     * Configures this sprite's orientation.
     */
    public void setOrientation (int orientation)
    {
        // if moving, assume the path will do the right thing when we arrive
        if (!isMoving()) {
            Quaternion quat = new Quaternion();
            quat.fromAngleAxis(ROTATIONS[orientation], UP);
            setLocalRotation(quat);
        }
    }

    /**
     * If the unit is on the ground, sets its z position based on the height of
     * the terrain, and its pitch and roll based on the slope of the terrain.
     * The units's x coordinate, y coordinate, and heading are unaffected.
     */
    public void snapToTerrain ()
    {
        // flyers simply fly from point to point
        if (_piece.isFlyer()) {
            return;
        }

        // adjust position to terrain height
        Vector3f pos = getLocalTranslation();
        TerrainNode tnode = _view.getTerrainNode();
        pos.z = tnode.getHeightfieldHeight(pos.x, pos.y);
        setLocalTranslation(pos);

        // adjust rotation to terrain slope
        Quaternion rot = getLocalRotation();
        Vector3f normal = tnode.getHeightfieldNormal(pos.x, pos.y),
            up = rot.mult(Vector3f.UNIT_Z),
            cross = up.cross(normal);
        float angle = FastMath.asin(cross.length());
        if (angle == 0f) {
            return;
        }
        Quaternion mod = new Quaternion();
        mod.fromAngleAxis(angle, cross);
        setLocalRotation(mod.multLocal(rot));
    }

    /**
     * Called when we receive an event indicating that our piece was updated in
     * some way.
     */
    public void updated (Piece piece, short tick)
    {
        _piece = (Piece)piece.clone();
        _tick = tick;
    }

    /**
     * Called when our piece is updated in such a way that we may have moved.
     * We compare our internal (to the sprite) last know position and
     * orientation to the orientation of our current piece, and effect any
     * necessary movement.
     *
     * @return true if this update resulted in the sprite being moved along a
     * path, in which case the view will wait for the movement to finish before
     * animating other bits.
     */
    public boolean updatePosition (BangBoard board)
    {
        boolean moved = false;

        // move ourselves to our new location if we have one
        if (_piece.x != _px || _piece.y != _py) {
//             log.info("Moving " + _piece.info() + " from +" +
//                      _piece.x + "+" + _piece.y + " to +" + _px + "+" + _py);
            moveSprite(board);
            moved = true;
            _px = _piece.x;
            _py = _piece.y;
        }

        // if we're rotated (which only happens in the editor), we need to
        // rotate our model
        if (_porient != _piece.orientation) {
            setOrientation(_porient = _piece.orientation);
            // now reset our location and it will adjust our centering
            int elev = computeElevation(board, _px, _py);
            setLocation(_px, _py, elev);
        }

        if (!isMoving() && moved) {
            log.warning("Moved but am not moving?! " + _piece.info());
        }

        // if we started moving as a result, we need to be waited for
        return isMoving();
    }

    /**
     * Called when our piece is removed from the board state.
     */
    public void removed ()
    {
//         // remove ourselves from the sprite manager and go away
//         if (_mgr != null) {
//             ((SpriteManager)_mgr).removeSprite(this);
//         }
    }

    @Override // documentation inherited
    public void pathCompleted ()
    {
        super.pathCompleted();
        updateCollisionTree();
    }

    /**
     * Sprites should create and attach their scene geometry by overriding
     * this method.
     */
    protected void createGeometry (BasicContext ctx)
    {
    }

    /**
     * Sprites can create and pre-load sounds they will need by overriding
     * this method.
     */
    protected void createSounds (SoundGroup sounds)
    {
    }

    /**
     * Computes the elevation for this piece at the specified location.
     */
    protected int computeElevation (BangBoard board, int tx, int ty)
    {
        int width = _piece.getWidth(), height = _piece.getHeight();
        if (width == 1 && height == 1) {
            return board.getHeightfieldElevation(tx, ty);
        }

        int elevation = Integer.MIN_VALUE;
        for (int y = ty, ymax = ty + height; y < ymax; y++) {
            for (int x = tx, xmax = tx + width; x < xmax; x++) {
                elevation = Math.max(elevation,
                    board.getHeightfieldElevation(x, y));
            }
        }
        return elevation;
    }

    /**
     * Called when a sprite has been updated with a new location. The
     * default implementation simply relocates the sprite instantly but
     * derived classes will want to compute a path and animate the sprite
     * traveling between its old and new locations.
     */
    protected void moveSprite (BangBoard board)
    {
        int elev = computeElevation(board, _piece.x, _piece.y);
        setLocation(_piece.x, _piece.y, elev);
    }

    /** Converts tile coordinates plus elevation into (3D) world
     * coordinates. */
    protected Vector3f toWorldCoords (int tx, int ty, int elev, Vector3f target)
    {
        target.x = tx * TILE_SIZE;
        target.y = ty * TILE_SIZE;
        target.z = elev * (TILE_SIZE / BangBoard.ELEVATION_UNITS_PER_TILE);
        centerWorldCoords(target);
        return target;
    }

    /** Adjusts the coordinates to the center of the piece's footprint. */
    protected void centerWorldCoords (Vector3f coords)
    {
        coords.x += TILE_SIZE/2;
        coords.y += TILE_SIZE/2;
    }

    /**
     * Binds the given animation and updates the set of emissions.
     *
     * @param random the number used to select a texture
     */
    protected void bindAnimation (BasicContext ctx, Model.Animation anim,
        int random)
    {
        _binding = anim.bind(this, random);

        // stop all running emissions
        for (SpriteEmission emission : _emissions.values()) {
            if (emission.isRunning()) {
                emission.stop();
            }
        }

        // start emissions used in the animation, creating any uncreated
        for (int ii = 0; ii < anim.emitters.length; ii++) {
            String name = anim.emitters[ii].name;
            SpriteEmission emission = _emissions.get(name);
            if (emission == null) {
                _emissions.put(name, emission = SpriteEmission.create(name,
                    anim.emitters[ii].props));
                emission.init(ctx, _view, this);
            }
            emission.start(anim, _binding);
        }
    }

    @Override // documentation inherited
    protected void setParent (Node parent)
    {
        super.setParent(parent);

        // clear our model binding and emissions when we're removed
        if (parent == null) {
            if (_binding != null) {
                _binding.detach();
            }
            for (SpriteEmission emission : _emissions.values()) {
                emission.cleanup();
            }
        }
    }

    protected BoardView _view;

    protected Piece _piece;
    protected int _px, _py, _porient;
    protected short _tick;

    protected boolean _selected;

    /** Most pieces have an underlying model, so we provide a reference. */
    protected Model _model;

    /** Sprites must bind animations from their model with this reference so
     * that we can properly clear the binding when the sprite is removed. */
    protected Model.Binding _binding;

    /** The emissions activated by animations. */
    protected HashMap<String, SpriteEmission> _emissions =
        new HashMap<String, SpriteEmission>();

    /** The current elevation of the piece. */
    protected int _elevation;

    /** When activated, causes all pieces to warp instead of smoothly
     * follow a path. */
    protected static boolean _editorMode;

    /** Used for temporary calculations. */
    protected static Vector3f _temp = new Vector3f();

    protected static float[] ROTATIONS = {
        0, // NORTH
        FastMath.PI/2, // EAST
        FastMath.PI, // SOUTH
        3*FastMath.PI/2, // WEST
    };
}
