//
// $Id$

package com.threerings.bang.editor;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.imageio.ImageIO;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import com.samskivert.util.IntListUtil;
import com.samskivert.util.ListUtil;
import com.samskivert.util.StringUtil;

import com.threerings.util.MessageBundle;

import com.threerings.presents.dobj.EntryAddedEvent;
import com.threerings.presents.dobj.EntryRemovedEvent;
import com.threerings.presents.dobj.EntryUpdatedEvent;
import com.threerings.presents.dobj.SetListener;

import com.threerings.crowd.client.PlaceView;
import com.threerings.crowd.data.PlaceConfig;
import com.threerings.crowd.data.PlaceObject;
import com.threerings.crowd.util.CrowdContext;

import com.threerings.parlor.game.client.GameController;

import com.threerings.bang.game.data.BangBoard;
import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.PieceDSet;
import com.threerings.bang.game.data.Terrain;
import com.threerings.bang.game.data.piece.Marker;
import com.threerings.bang.game.data.piece.Piece;
import com.threerings.bang.game.data.piece.PieceCodes;
import com.threerings.bang.game.data.piece.Track;
import com.threerings.bang.server.persist.BoardRecord;

import static com.threerings.bang.Log.log;

/**
 * Handles the logic and flow for the Bang! board editor.
 */
public class EditorController extends GameController
    implements PieceCodes
{
    /** Requests that we terminate the editor. */
    public static final String EXIT = "Exit";
    
    /** Instructs us to start a new board. */
    public static final String NEW_BOARD = "NewBoard";
    
    /** Instructs us to load a new board. */
    public static final String LOAD_BOARD = "LoadBoard";

    /** Instructs us to save the current board. */
    public static final String SAVE_BOARD = "SaveBoard";

    /** Instructs us to import a heightfield. */
    public static final String IMPORT_HEIGHTFIELD = "ImportHeightfield";
    
    /** Instructs us to export a heightfield. */
    public static final String EXPORT_HEIGHTFIELD = "ExportHeightfield";
    
    /** Instructs us to undo the last edit. */
    public static final String UNDO = "Undo";
    
    /** Instructs us to redo the last undone edit. */
    public static final String REDO = "Redo";
    
    /** Instructs us to bring up the light properties dialog. */
    public static final String EDIT_LIGHT = "EditLight";
    
    /** Instructs us to bring up the sky properties dialog. */
    public static final String EDIT_SKY = "EditSky";
    
    /** Instructs us to bring up the water properties dialog. */
    public static final String EDIT_WATER = "EditWater";
    
    /** Instructs us to bring up the environment properties dialog. */
    public static final String EDIT_ENVIRONMENT = "EditEnvironment";
    
    /** Instructs us to bring up the board properties dialog. */
    public static final String EDIT_BOARD_PROPERTIES = "EditBoardProperties";
    
    /** Instructs us to toggle wireframe rendering. */
    public static final String TOGGLE_WIREFRAME = "ToggleWireframe";
    
    /** Instructs us to toggle the tile grid. */
    public static final String TOGGLE_GRID = "ToggleGrid";
    
    /** Instructs us to toggle the impassable tile highlights. */
    public static final String TOGGLE_HIGHLIGHTS = "ToggleHighlights";
    
    /** Instructs us to generate the static terrain shadows. */
    public static final String GENERATE_SHADOWS = "GenerateShadows";
    
    /** Instructs us to clear the static terrain shadows. */
    public static final String CLEAR_SHADOWS = "ClearShadows";
    
    /**
     * The superclass of actions that can be undone and redone.
     */
    public interface Edit
    {
        /** Undoes this edit. */
        public void undo ();
        
        /** Redoes this edit. */
        public void redo ();
    }
    
    /** Adds a piece to the board. */
    public void addPiece (Piece piece)
    {
        piece.assignPieceId(_bangobj);
        _bangobj.addToPieces(piece);
        addEdit(new PieceAdded(piece));
    }

    /** Removes a piece from the board. */
    public void removePiece (Piece piece)
    {
        _bangobj.removeFromPieces(piece.getKey());
        addEdit(new PieceRemoved(piece));
    }

    /**
     * Lays a piece of track at the specified coordinates.
     *
     * @return true if track was laid, false if there was already a piece of
     * track there
     */
    public boolean layTrack (Point tcoords)
    {
        Track track = getTrack(tcoords.x, tcoords.y);
        if (track != null) {
            return false;
        }
        // add and update the piece and its neighbors, if any
        track = new Track();
        track.position(tcoords.x, tcoords.y);
        track.assignPieceId(_bangobj);
        _bangobj.addToPieces(track);
        updateTrack(track);
        updateTrack(getTrack(tcoords.x - 1, tcoords.y));
        updateTrack(getTrack(tcoords.x + 1, tcoords.y));
        updateTrack(getTrack(tcoords.x, tcoords.y - 1));
        updateTrack(getTrack(tcoords.x, tcoords.y + 1));
        return true;
    }
    
    /**
     * Removes the piece of track at the specified coordinates.
     *
     * @return true if a piece of track was removed, false if there was no
     * piece of track there
     */
    public boolean removeTrack (Point tcoords)
    {
        Track track = getTrack(tcoords.x, tcoords.y);
        if (track == null) {
            return false;
        }
        // remove and update the neighbors, if any
        _bangobj.removeFromPieces(track.getKey());
        updateTrack(getTrack(tcoords.x - 1, tcoords.y));
        updateTrack(getTrack(tcoords.x + 1, tcoords.y));
        updateTrack(getTrack(tcoords.x, tcoords.y - 1));
        updateTrack(getTrack(tcoords.x, tcoords.y + 1));
        return true;
    }
    
    /** Starts an edit on the specified piece, if not already started. */
    public void maybeStartPieceEdit (Piece piece)
    {
        if (_pedit != null && _pedit.saved.equals(piece)) {
            return;
        }
        if (_pedit != null) {
            addEdit(_pedit);
        }
        _pedit = new PieceUpdated(piece);
    }
    
    /** Commits an edit on the specified piece, if one has started. */
    public void maybeCommitPieceEdit ()
    {
        if (_pedit == null) {
            return;
        }
        addEdit(_pedit);
        _pedit = null;
    }
    
    /** Adds an edit to the undo stack. */
    public void addEdit (Edit edit)
    {
        if (_undoStack.size() == UNDO_STACK_MAXIMUM) {
            _undoStack.remove(0);
        }
        _undoStack.add(edit);
        _redoStack.clear();
        
        _panel.undo.setEnabled(true);
        _panel.redo.setEnabled(false);
    }
    
    /** Clears the undo stack, for example when we have loaded a new board. */
    public void clearEdits ()
    {
        _undoStack.clear();
        _redoStack.clear();
        _panel.undo.setEnabled(false);
        _panel.redo.setEnabled(false);
    }
    
    /** Handles a request to exit the editor. Generated by the {@link
     * #EXIT} command. */
    public void handleExit (Object source)
    {
        // TODO: warn about an unsaved board
        System.exit(0);
    }
    
    /** Handles a request to create a new board.  Generated by the
     * {@link #NEW_BOARD} command. */
    public void handleNewBoard (Object source)
    {
        if (_newBoard == null) {
            _newBoard = new NewBoardDialog(_ctx, _panel);
        }
        _newBoard.fromBoard(_bangobj.board);
        _newBoard.setLocation(100, 100);
        _newBoard.setLocationRelativeTo(_ctx.getFrame());
        _newBoard.setVisible(true);
    }
    
    /** Handles a request to load the current board.  Generated by the
     * {@link #LOAD_BOARD} command. */
    public void handleLoadBoard (Object source)
    {
        if (_boardChooser == null) {
            _boardChooser = new JFileChooser(_board.getParent());
        }
        int rv = _boardChooser.showOpenDialog(_ctx.getFrame());
        if (rv != JFileChooser.APPROVE_OPTION) {
            return;
        }
        loadBoard(_boardChooser.getSelectedFile(), true);
    }

    /** Handles a request to save the current board.  Generated by the
     * {@link #SAVE_BOARD} command. */
    public void handleSaveBoard (Object source)
    {
        if (_boardChooser == null) {
            _boardChooser = new JFileChooser(_board.getParent());
        }
        int rv = _boardChooser.showSaveDialog(_ctx.getFrame());
        if (rv != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            File board = _boardChooser.getSelectedFile();
            BoardRecord brec = new BoardRecord();
            _panel.info.toBoard(brec);
            brec.setData(_bangobj.board, _bangobj.getPieceArray());
            brec.save(board);
            _board = board;
            _ctx.setWindowTitle(_board.toString());
            _ctx.displayStatus(_msgs.get("m.saved", _board));

        } catch (IOException ioe) {
            _ctx.displayStatus(_msgs.get("m.save_error", ioe.getMessage()));
        }
    }

    /** Handles a request to import a heightfield.  Generated by the
     * {@link #IMPORT_HEIGHTFIELD} command. */
    public void handleImportHeightfield (Object source)
    {
        if (_imageChooser == null) {
            createImageChooser();
        }
        int rv = _imageChooser.showOpenDialog(_ctx.getFrame());
        if (rv != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        try {
            File heightfield = _imageChooser.getSelectedFile();
            _panel.view.setHeightfield(ImageIO.read(heightfield));
            _ctx.displayStatus(_msgs.get("m.imported", heightfield));
            
        } catch (IOException ioe) {
            _ctx.displayStatus(_msgs.get("m.import_error", ioe.getMessage()));
        }
    }
    
    /** Handles a request to export a heightfield.  Generated by the
     * {@link #EXPORT_HEIGHTFIELD} command. */
    public void handleExportHeightfield (Object source)
    {
        if (_imageChooser == null) {
            createImageChooser();
        }
        int rv = _imageChooser.showSaveDialog(_ctx.getFrame());
        if (rv != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        try {
            File heightfield = _imageChooser.getSelectedFile();
            String name = heightfield.getName(),
                suffix = name.substring(name.indexOf('.')+1);
            ImageIO.write(_panel.view.getHeightfieldImage(), suffix,
                heightfield);
            _ctx.displayStatus(_msgs.get("m.exported", heightfield));
            
        } catch (IOException ioe) {
            _ctx.displayStatus(_msgs.get("m.export_error", ioe.getMessage()));
        }
    }
    
    /** Handles a request to undo the last action.  Generated by the
     * {@link #UNDO} command. */
    public void handleUndo (Object source)
    {
        if (_undoStack.isEmpty()) {
            return;
        }
        Edit last = _undoStack.remove(_undoStack.size() - 1);
        last.undo();
        _redoStack.add(last);
        
        _panel.undo.setEnabled(!_undoStack.isEmpty());
        _panel.redo.setEnabled(true);
    }
    
    /** Handles a request to redo the last undone action.  Generated by the
     * {@link #REDO} command. */
    public void handleRedo (Object source)
    {
        if (_redoStack.isEmpty()) {
            return;
        }
        Edit last = _redoStack.remove(_redoStack.size() - 1);
        last.redo();
        _undoStack.add(last);
        
        _panel.undo.setEnabled(true);
        _panel.redo.setEnabled(!_redoStack.isEmpty());
    }
    
    /** Handles a request to edit the light properties.  Generated by the
     * {@link #EDIT_LIGHT} command. */
    public void handleEditLight (Object source)
    {
        if (_light == null) {
            _light = new LightDialog(_ctx, _panel);
        }
        _light.fromBoard(_bangobj.board);
        _light.setLocation(100, 100);
        _light.setLocationRelativeTo(_ctx.getFrame());
        _light.setVisible(true);
    }
    
    /** Handles a request to edit the sky properties.  Generated by the
     * {@link #EDIT_SKY} command. */
    public void handleEditSky (Object source)
    {
        if (_sky == null) {
            _sky = new SkyDialog(_ctx, _panel);
        }
        _sky.fromBoard(_bangobj.board);
        _sky.setLocation(100, 100);
        _sky.setLocationRelativeTo(_ctx.getFrame());
        _sky.setVisible(true);
    }
    
    /** Handles a request to edit the water properties.  Generated by the
     * {@link #EDIT_WATER} command. */
    public void handleEditWater (Object source)
    {
        if (_water == null) {
            _water = new WaterDialog(_ctx, _panel);
        }
        _water.fromBoard(_bangobj.board);
        _water.setLocation(100, 100);
        _water.setLocationRelativeTo(_ctx.getFrame());
        _water.setVisible(true);
    }
    
    /** Handles a request to edit the environment properties.  Generated by the
     * {@link #EDIT_ENVIRONMENT} command. */
    public void handleEditEnvironment (Object source)
    {
        if (_environment == null) {
            _environment = new EnvironmentDialog(_ctx, _panel);
        }
        _environment.fromBoard(_bangobj.board);
        _environment.setLocation(100, 100);
        _environment.setLocationRelativeTo(_ctx.getFrame());
        _environment.setVisible(true);
    }
    
    /** Handles a request to edit the board properties.  Generated by the
     * {@link #EDIT_BOARD_PROPERTIES} command. */
    public void handleEditBoardProperties (Object source)
    {
        if (_boardProperties == null) {
            _boardProperties = new BoardPropertiesDialog(_ctx, _panel);
        }
        _boardProperties.fromBoard(_bangobj.board);
        _boardProperties.setLocation(100, 100);
        _boardProperties.setLocationRelativeTo(_ctx.getFrame());
        _boardProperties.setVisible(true);
    }
    
    /** Handles a request to toggle wireframe rendering.  Generated by the
     * {@link #TOGGLE_WIREFRAME} command. */
    public void handleToggleWireframe (Object source)
    {
        _panel.view.toggleWireframe();
    }
    
    /** Handles a request to toggle the tile grid.  Generated by the
     * {@link #TOGGLE_GRID} command. */
    public void handleToggleGrid (Object source)
    {
        _panel.view.toggleGrid(false);
    }
    
    /** Handles a request to toggle the highlights.  Generated by the
     * {@link #TOGGLE_HIGHLIGHTS} command. */
    public void handleToggleHighlights (Object source)
    {
        _panel.view.toggleHighlights();
    }
    
    /** Handles a request to generate the static terrain shadows.  Generated by
     * the {@link #GENERATE_SHADOWS} command. */
    public void handleGenerateShadows (Object source)
    {
        _panel.view.getTerrainNode().generateShadows();
        _panel.view.getTerrainNode().refreshShadows();
    }
    
    /** Handles a request to clear the static terrain shadows.  Generated by
     * the {@link #CLEAR_SHADOWS} command. */
    public void handleClearShadows (Object source)
    {
        _bangobj.board.fillShadows(0);
        _panel.view.getTerrainNode().refreshShadows();
    }
    
    /** Handles a request to select a tool.  Generated by the
     * {@link #SELECT_TOOL} command. */
    public void handleSelectTool (Object source, String name)
    {
        _panel.tools.selectTool(name);
    }
    
    // documentation inherited
    public void init (CrowdContext ctx, PlaceConfig config)
    {
        super.init(ctx, config);
        _ctx = (EditorContext)ctx;
        _config = (EditorConfig)config;
        _msgs = _ctx.getMessageManager().getBundle("editor");
    }

    // documentation inherited
    public void willEnterPlace (PlaceObject plobj)
    {
        super.willEnterPlace(plobj);
        _bangobj = (BangObject)plobj;
        _bangobj.addListener(_pclistener);
    }

    protected void loadBoard (File board, boolean refresh)
    {
        try {
            BoardRecord brec = new BoardRecord();
            brec.load(board);
            _bangobj.board = brec.getBoard();
            Piece[] pieces = brec.getPieces();
            // reassign piece ids
            for (int ii = 0; ii < pieces.length; ii++) {
                pieces[ii].pieceId = (ii+1);
            }
            _bangobj.maxPieceId = pieces.length;
            _bangobj.setPieces(new PieceDSet(pieces));

            if (refresh) {
                _panel.view.refreshBoard();
            }

            _panel.info.fromBoard(brec);
            updatePlayerCount();
            _board = board;
            _ctx.setWindowTitle(_board.toString());
            _ctx.displayStatus(_msgs.get("m.loaded", _board));

        } catch (IOException ioe) {
            _ctx.displayStatus(_msgs.get("m.load_error", ioe.getMessage()));
        }
    }

    /**
     * Creates and initializes the image file chooser.
     */
    protected void createImageChooser ()
    {
        _imageChooser = new JFileChooser(_board.getParent());
        _imageChooser.setFileFilter(new FileFilter() {
            public boolean accept (File f) {
                if (f.isDirectory()) {
                    return true;
                }
                String name = f.getName(),
                    suffix = name.substring(name.lastIndexOf('.')+1);
                return ListUtil.contains(ImageIO.getReaderFormatNames(),
                    suffix) &&
                    ListUtil.contains(ImageIO.getWriterFormatNames(), suffix);
            }
            public String getDescription () {
                return _msgs.get("m.hf_images");
            }
        });
    }
    
    @Override // documentation inherited
    protected PlaceView createPlaceView (CrowdContext ctx)
    {
        _panel = new EditorPanel((EditorContext)ctx, this);
        return _panel;
    }

    @Override // documentation inherited
    protected void gameDidStart ()
    {
        super.gameDidStart();

        // load up any board specified on the command line
        if (EditorApp.appArgs.length > 0 &&
            !StringUtil.isBlank(EditorApp.appArgs[0])) {
            loadBoard(new File(EditorApp.appArgs[0]), false);
        } else {
            _bangobj.board = new BangBoard(32, 32);
            _bangobj.board.fillTerrain(Terrain.DIRT);
            _bangobj.setPieces(new PieceDSet());
        }

        // our panel needs to do some game starting business
        _panel.startEditing(_bangobj, _config);
    }

    @Override // documentation inherited
    protected void gameWillReset ()
    {
        super.gameWillReset();
        _panel.endEditing();
    }

    @Override // documentation inherited
    protected void gameDidEnd ()
    {
        super.gameDidEnd();
        _panel.endEditing();
    }

    protected void updatePlayerCount ()
    {
        int pcount = 0;
        for (Iterator iter = _bangobj.pieces.iterator(); iter.hasNext(); ) {
            if (Marker.isMarker((Piece)iter.next(), Marker.START)) {
                pcount++;
            }
        }
        _panel.info.updatePlayers(pcount);
    }

    /**
     * Sets the type and orientation of a piece of track based on the sides
     * on which it has neighboring pieces.  Silently ignores null tracks.
     */
    protected void updateTrack (Track track)
    {
        if (track == null) {
            return;
        }
        track = (Track)track.clone();
        int[] neighbors = // n, e, s, w
            new int[] { getTrack(track.x, track.y - 1) == null ? 0 : 1,
                getTrack(track.x + 1, track.y) == null ? 0 : 1,
                getTrack(track.x, track.y + 1) == null ? 0 : 1,
                getTrack(track.x - 1, track.y) == null ? 0 : 1 };
        int ncount = IntListUtil.sum(neighbors);
        
        // except for turns, the values of the types are all equal to the
        // number of neighbors
        if (ncount == 2 && neighbors[EAST] != neighbors[WEST]) {
            track.type = Track.TURN;
            
        } else {
            track.type = (byte)ncount;
        }
        
        // orientation doesn't matter for singletons and cross junctions
        if (track.type == Track.TERMINAL) {
            track.orientation = (short)IntListUtil.indexOf(neighbors, 1);
            
        } else if (track.type == Track.STRAIGHT) {
            track.orientation = (short)(neighbors[EAST] == 1 ? EAST : NORTH);
        
        } else if (track.type == Track.T_JUNCTION) {
            track.orientation = (short)IntListUtil.indexOf(neighbors, 0);
            
        } else if (track.type == Track.TURN) {
            track.orientation = (short)(neighbors[NORTH] == 1 ?
                (neighbors[EAST] == 1 ? NORTH : WEST) :
                (neighbors[EAST] == 1 ? EAST : SOUTH));
        }
        
        _bangobj.updatePieces(track);
    }
    
    /**
     * Returns the piece of track at the specified tile coordinates, or
     * <code>null</code> if there isn't one.
     */
    protected Track getTrack (int tx, int ty)
    {
        for (Iterator it = _bangobj.pieces.iterator(); it.hasNext(); ) {
            Piece piece = (Piece)it.next();
            if (piece.x == tx && piece.y == ty && piece instanceof Track) {
                return (Track)piece;
            }
        }
        return null;
    }
    
    /** A piece addition edit. */
    protected class PieceAdded
        implements Edit
    {
        public PieceAdded (Piece piece)
        {
            _piece = piece;
        }
        
        // documentation inherited from interface Edit
        public void undo ()
        {
            _bangobj.removeFromPieces(_piece.getKey());
        }
        
        // documentation inherited from interface Edit
        public void redo ()
        {
            _bangobj.addToPieces(_piece);
        }
        
        /** The added piece. */
        protected Piece _piece;
    }
    
    /** A piece removal edit. */
    protected class PieceRemoved
        implements Edit
    {
        public PieceRemoved (Piece piece)
        {
            _piece = piece;
        }
        
        // documentation inherited from interface Edit
        public void undo ()
        {
            _bangobj.addToPieces(_piece);
        }
        
        // documentation inherited from interface Edit
        public void redo ()
        {
            _bangobj.removeFromPieces(_piece.getKey());
        }
        
        /** The removed piece. */
        protected Piece _piece;
    }
    
    /** A piece update edit. */
    protected class PieceUpdated
        implements Edit
    {
        /** The piece before the update (if done) or after (if undone). */
        public Piece saved;
        
        public PieceUpdated (Piece saved)
        {
            this.saved = saved;
        }
        
        // documentation inherited from interface Edit
        public void undo ()
        {
            swapSaved();
        }
        
        // documentation inherited from interface Edit
        public void redo ()
        {
            swapSaved();
        }
        
        /**
         * Swaps the current piece with the saved piece.
         */
        protected void swapSaved ()
        {
            Piece tmp = (Piece)_bangobj.pieces.get(saved.getKey());
            _bangobj.updatePieces(saved);
            saved = tmp;
        }
    }
    
    /** Listens for piece additions and removals. */
    protected SetListener _pclistener = new SetListener() {
        public void entryAdded (EntryAddedEvent event) {
            updatePlayerCount();
        }
        public void entryUpdated (EntryUpdatedEvent event) {
        }
        public void entryRemoved (EntryRemovedEvent event) {
            updatePlayerCount();
        }
    };

    /** A casted reference to our context. */
    protected EditorContext _ctx;

    /** The configuration of this game. */
    protected EditorConfig _config;

    /** Used to translate messages. */
    protected MessageBundle _msgs;

    /** Contains our main user interface. */
    protected EditorPanel _panel;

    /** A casted reference to our game object. */
    protected BangObject _bangobj;

    /** The lists of actions that can be undone and redone. */
    protected ArrayList<Edit> _undoStack = new ArrayList<Edit>(),
        _redoStack = new ArrayList<Edit>();
    
    /** The currently active piece edit, if any. */
    protected PieceUpdated _pedit;
    
    /** The file chooser we use for loading and saving boards. */
    protected JFileChooser _boardChooser;

    /** The file chooser we use for importing and exporting images. */
    protected JFileChooser _imageChooser;
    
    /** The new board dialog. */
    protected NewBoardDialog _newBoard;
    
    /** The light properties dialog. */
    protected LightDialog _light;
    
    /** The sky properties dialog. */
    protected SkyDialog _sky;
    
    /** The water properties dialog. */
    protected WaterDialog _water;
    
    /** The environment properties dialog. */
    protected EnvironmentDialog _environment;
    
    /** The board properties dialog. */
    protected BoardPropertiesDialog _boardProperties;
    
    /** A reference to the file associated with the board we're editing. */
    protected File _board = new File("");
    
    /** The maximum number of edits to keep on the undo stack. */
    protected static final int UNDO_STACK_MAXIMUM = 20;
}
