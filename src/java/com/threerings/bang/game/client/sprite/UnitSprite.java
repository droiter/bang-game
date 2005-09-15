//
// $Id$

package com.threerings.bang.game.client.sprite;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;

import com.jme.image.Texture;
import com.jme.math.FastMath;
import com.jme.math.Quaternion;
import com.jme.math.Vector3f;
import com.jme.renderer.Camera;
import com.jme.renderer.ColorRGBA;
import com.jme.renderer.Renderer;
import com.jme.scene.BillboardNode;
import com.jme.scene.Node;
import com.jme.scene.Spatial;
import com.jme.scene.shape.Quad;
import com.jme.scene.state.TextureState;
import com.jme.util.TextureManager;

import com.threerings.bang.client.Model;
import com.threerings.bang.game.data.BangBoard;
import com.threerings.bang.game.data.piece.Piece;
import com.threerings.bang.game.data.piece.Unit;
import com.threerings.bang.util.BangContext;
import com.threerings.bang.util.RenderUtil;

import static com.threerings.bang.Log.log;
import static com.threerings.bang.client.BangMetrics.*;

/**
 * Displays a particular unit.
 */
public class UnitSprite extends MobileSprite
{
    public UnitSprite (String type)
    {
        super("units", type);
    }

    /**
     * Indicates that the mouse is hovering over this piece.
     */
    public void setHovered (boolean hovered)
    {
        _hovquad.setForceCull(!hovered);
    }

    /**
     * Indicates that this piece is a potential target.
     */
    public void setTargeted (boolean targeted)
    {
        if (_pendingTick == -1) {
            _tgtquad.setSolidColor(ColorRGBA.white);
            _tgtquad.setForceCull(!targeted);
        }
    }

    /**
     * Indicates that we have requested to shoot this piece but it is not
     * yet confirmed by the server.
     */
    public void setPendingShot (boolean pending)
    {
        if (pending) {
            if (_pendingTick == -1) {
                _tgtquad.setSolidColor(ColorRGBA.red);
            }
            _pendingTick = _tick;
        } else {
            _pendingTick = -1;
        }
        _tgtquad.setForceCull(!pending);
    }

    /**
     * Indicates that we have queued up an action to be taken when our
     * piece is once again able to move and shoot.
     */
    public void setPendingAction (boolean pending)
    {
        _pendquad.setForceCull(!pending);
    }

    @Override // documentation inherited
    public void updated (BangBoard board, Piece piece, short tick)
    {
        super.updated(board, piece, tick);

        Unit unit = (Unit)piece;
        int ticks;

        // clear our pending shot once we've been ticked
        if (_pendingTick != -1 && tick > _pendingTick) {
            setPendingShot(false);
        }

        // update our status display
        _status.setForceCull(!unit.isAlive());
        if ((ticks = unit.ticksUntilMovable(_tick)) > 0) {
            _ticks.setRenderState(_ticktex[Math.max(0, 4-ticks)]);
            _ticks.updateRenderState();
            _movable.setForceCull(true);
        } else {
            _ticks.setRenderState(_ticktex[4]);
            _ticks.updateRenderState();
            _movable.setForceCull(false);
        }

        // update our colors in the event that our owner changes
        configureOwnerColors();

        // update our damage texture if necessary
        if (unit.damage != _odamage) {
            _damtex.setTexture(createDamageTexture());
            _damage.updateRenderState();
            _odamage = unit.damage;
        }

        // update our icon if necessary
        if (unit.benuggeted && _icon.isForceCulled()) {
            _icon.setRenderState(_nugtex);
            _icon.updateRenderState();
            _icon.setForceCull(false);
        } else if (!unit.benuggeted && !_icon.isForceCulled()) {
            _icon.setForceCull(true);
        }
    }

    @Override // documentation inherited
    public boolean isSelectable ()
    {
//        return (_piece.ticksUntilMovable(_tick) == 0);
        return true;
    }

    @Override // documentation inherited
    protected void createGeometry (BangContext ctx)
    {
        if (_hovtex == null) {
            loadTextures(ctx);
        }

        // this icon is displayed when the mouse is hovered over us
        _hovquad = RenderUtil.createIcon(_hovtex);
//         _hovquad.setRenderQueueMode(Renderer.QUEUE_TRANSPARENT);
        _hovquad.setLocalTranslation(new Vector3f(0, 0, 0.2f));
        attachChild(_hovquad);
        _hovquad.setForceCull(true);

        // this composite of icons combines to display our status
        _status = new StatusNode();
        _status.setRenderState(RenderUtil.blendAlpha);
//         _status.setRenderQueueMode(Renderer.QUEUE_TRANSPARENT);
        _status.updateRenderState();
        _status.setLocalTranslation(new Vector3f(0, 0, 0.1f));
        attachChild(_status);
        _ticks = RenderUtil.createIcon(TILE_SIZE/2, TILE_SIZE/2);
        _ticks.setLocalTranslation(new Vector3f(-TILE_SIZE/4, TILE_SIZE/4, 0));
        int tick = _piece.ticksUntilMovable(_tick), tidx = Math.max(0, 4-tick);
        _ticks.setRenderState(_ticktex[tidx]);
        _ticks.updateRenderState();
        _status.attachChild(_ticks);

        _damage = RenderUtil.createIcon(TILE_SIZE/2, TILE_SIZE/2);
        _damage.setLocalTranslation(new Vector3f(TILE_SIZE/4, TILE_SIZE/4, 0));
        _damtex = ctx.getRenderer().createTextureState();
        _damtex.setEnabled(true);
        _damtex.setTexture(createDamageTexture());
        _damage.setRenderState(_damtex);
        _damage.updateRenderState();
        _status.attachChild(_damage);

        _movable = RenderUtil.createIcon(TILE_SIZE, TILE_SIZE/2);
        _movable.setLocalTranslation(new Vector3f(0, -TILE_SIZE/4, 0));
        _movable.setRenderState(_movetex);
        _movable.updateRenderState();
        _status.attachChild(_movable);
        attachChild(_status);
        _movable.setForceCull(tick > 0);

        // configure our colors
        configureOwnerColors();

        // load up our model
        super.createGeometry(ctx);

        // this icon is displayed when we're a target
        _tgtquad = RenderUtil.createIcon(_tgttex);
        _tgtquad.setLocalTranslation(new Vector3f(0, 0, 0));
//         _tgtquad.setRenderQueueMode(Renderer.QUEUE_TRANSPARENT);
        _tgtquad.setRenderState(RenderUtil.alwaysZBuf);
        _tgtquad.updateRenderState();
        BillboardNode bbn = new BillboardNode("target");
        bbn.setLocalTranslation(new Vector3f(0, 0, TILE_SIZE/3));
        bbn.attachChild(_tgtquad);
        attachChild(bbn);
        _tgtquad.setForceCull(true);

        // this icon is displayed when we have a pending action queued
        _pendquad = RenderUtil.createIcon(5, 5);
        _pendquad.setRenderState(_pendtex);
        _pendquad.setLocalTranslation(new Vector3f(0, 0, 0));
//         _pendquad.setRenderQueueMode(Renderer.QUEUE_TRANSPARENT);
        _pendquad.setRenderState(RenderUtil.alwaysZBuf);
        _pendquad.updateRenderState();
        bbn = new BillboardNode("pending");
        bbn.setLocalTranslation(new Vector3f(0, 0, TILE_SIZE/3));
        bbn.attachChild(_pendquad);
        attachChild(bbn);
        _pendquad.setForceCull(true);

        // this icon is displayed when we are modified in some way (we're
        // carrying a nugget, for example)
        _icon = RenderUtil.createIcon(5, 5);
        _icon.setLocalTranslation(new Vector3f(0, 0, 0));
//         _icon.setRenderQueueMode(Renderer.QUEUE_TRANSPARENT);
        _icon.setRenderState(RenderUtil.alwaysZBuf);
        _icon.updateRenderState();
        bbn = new BillboardNode("icon");
        bbn.setLocalTranslation(new Vector3f(0, 0, TILE_SIZE/3));
        bbn.attachChild(_icon);
        attachChild(bbn);
        _icon.setForceCull(true);
    }

    @Override // documentation inherited
    protected int computeElevation (BangBoard board, int tx, int ty)
    {
        int offset = 0;
        if (_piece.isFlyer()) {
            offset = board.getElevation(tx, ty);
        }
        return super.computeElevation(board, tx, ty) + offset;
    }

    /** Sets up our colors according to our owning player. */
    protected void configureOwnerColors ()
    {
        _ticks.setSolidColor(JPIECE_COLORS[_piece.owner]);
        _ticks.updateRenderState();
        _damage.setSolidColor(JPIECE_COLORS[_piece.owner]);
        _damage.updateRenderState();
        _movable.setSolidColor(JPIECE_COLORS[_piece.owner]);
        _movable.updateRenderState();
    }

    /** Converts tile coordinates plus elevation into (3D) world
     * coordinates. */
    protected Vector3f toWorldCoords (int tx, int ty, int elev, Vector3f target)
    {
        // flyers are always up in the air
        elev = _piece.isFlyer() ? 2 : elev;
        return super.toWorldCoords(tx, ty, elev, target);
    }

    protected Texture createDamageTexture ()
    {
        int width = _dempty.getWidth(), height = _dempty.getHeight();
        BufferedImage comp = new BufferedImage(
            width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D gfx = (Graphics2D)comp.getGraphics();
        try {
            gfx.drawImage(_dempty, 0, 0, null);
            float percent = (100 - _piece.damage) / 100f;
            float extent = percent * (90 - 2*ARC_INSETS);
            // expand the width and height a smidge to avoid funny
            // business around the edges
            Arc2D.Float arc = new Arc2D.Float(
                -5*width/4, -height/4, 10*width/4, 10*height/4,
                90 - ARC_INSETS - extent, extent, Arc2D.PIE);
            gfx.setClip(arc);
            gfx.drawImage(_dfull, 0, 0, null);

        } finally {
            gfx.dispose();
        }

        return TextureManager.loadTexture(
            comp, Texture.MM_LINEAR_LINEAR, Texture.FM_LINEAR, true);
    }

    protected static void loadTextures (BangContext ctx)
    {
        _hovtex = RenderUtil.createTexture(
            ctx, ctx.loadImage("textures/ustatus/selected.png"));
        _tgttex = RenderUtil.createTexture(
            ctx, ctx.loadImage("textures/ustatus/crosshairs.png"));
        _pendtex = RenderUtil.createTexture(
            ctx, ctx.loadImage("textures/ustatus/pending.png"));
        _movetex = RenderUtil.createTexture(
            ctx, ctx.loadImage("textures/ustatus/tick_ready.png"));
        _nugtex = RenderUtil.createTexture(
            ctx, ctx.loadImage("textures/ustatus/nugget.png"));
        _ticktex = new TextureState[5];
        for (int ii = 0; ii < 5; ii++) {
            _ticktex[ii] = RenderUtil.createTexture(
                ctx, ctx.loadImage(
                    "textures/ustatus/tick_counter_" + ii + ".png"));
        }
        _dfull = ctx.loadImage("textures/ustatus/health_meter_full.png");
        _dempty = ctx.loadImage("textures/ustatus/health_meter_empty.png");
    }

    /** A node that rotates itself around the up vector as the camera
     * rotates so as to keep the status textures properly oriented toward
     * the player. */
    protected class StatusNode extends Node
    {
        public StatusNode () {
            super("status");
        }

	public void updateWorldData (float time) {
            _lastUpdate = time;
            updateWorldBound();
	}

	public void draw (Renderer r) {
            Camera cam = r.getCamera();

            // obtain our current world coordinates
            worldScale.set(parent.getWorldScale()).multLocal(localScale);
            worldTranslation = parent.getWorldRotation().mult(
                localTranslation, worldTranslation).multLocal(
                    parent.getWorldScale()).addLocal(
                        parent.getWorldTranslation());
            // we don't want our parent's world rotation, which would
            // normally by obtained like so:
            // parent.getWorldRotation().mult(localRotation, worldRotation);
            worldRotation.set(localRotation);

            // project the camera forward vector onto the "ground":
            // camdir - (camdir . UP) * UP
            Vector3f camdir = cam.getDirection();
            UP.mult(camdir.dot(UP), _tvec);
            camdir.subtract(_tvec, _tvec);
            _tvec.normalizeLocal();

            // compute the angle between LEFT and the camera direction to
            // find the camera rotation around the up vector
            _tvec.normalizeLocal();
            float theta = FastMath.acos(_tvec.dot(LEFT));
            // when y is negative, we need to flip the sign of the angle
            if (_tvec.y < 0) {
                theta *= -1f;
            }
            // we offset theta by PI/2 because our "natural" orientation
            // is a bit sideways
            _tquat.fromAngleAxis(theta + FastMath.PI/2, UP);
            worldRotation.multLocal(_tquat);

            // now we can update our children
            for (int ii = 0, ll = children.size(); ii < ll; ii++) {
                Spatial child = (Spatial)children.get(ii);
                if (child != null) {
                    child.updateGeometricState(_lastUpdate, false);
                }
            }

            super.draw(r);
	}

        protected float _lastUpdate;
    }

    protected Quad _hovquad, _tgtquad, _pendquad;

    protected StatusNode _status;
    protected Quad _ticks, _damage, _movable, _icon;
    protected TextureState _damtex;

    protected int _odamage;
    protected short _pendingTick = -1;

    protected static Vector3f _tvec = new Vector3f();
    protected static Quaternion _tquat = new Quaternion();

    protected static BufferedImage _dfull, _dempty;
    protected static TextureState _hovtex, _tgttex, _pendtex, _movetex, _nugtex;
    protected static TextureState[] _ticktex;

    protected static final float DBAR_WIDTH = TILE_SIZE-2;
    protected static final float DBAR_HEIGHT = (TILE_SIZE-2)/6f;

    /** Defines the amount by which the damage arc image is inset from a
     * full quarter circle (on each side): 8 degrees. */
    protected static final float ARC_INSETS = 7;
}
