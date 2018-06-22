/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2018 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package tracing;

import com.jidesoft.swing.SearchableBar;
import com.jidesoft.swing.TreeSearchable;

import ij.ImagePlus;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.imagej.ImageJ;
import net.imagej.table.DefaultGenericTable;

import org.scijava.command.CommandService;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;

import tracing.analysis.TreeAnalyzer;
import tracing.gui.ColorMenu;
import tracing.gui.GuiUtils;
import tracing.gui.SwingSafeResult;
import tracing.plugin.DistributionCmd;
import tracing.plugin.ROIExporterCmd;
import tracing.plugin.SkeletonConverter;
import tracing.plugin.TreeColorizerCmd;
import tracing.util.SWCColor;

/**
 * Creates the "Path Manager" JFrame.
 * 
 * @author Tiago Ferreira
 */
public class PathManagerUI extends JFrame implements PathAndFillListener, TreeSelectionListener {

	private static final long serialVersionUID = 1L;
	private HelpfulJTree tree;
	private DefaultMutableTreeNode root;
	private SimpleNeuriteTracer plugin;
	private PathAndFillManager pathAndFillManager;
	private DefaultGenericTable table;
	private boolean tableSaved;
	protected SwingWorker<Object, Object> fitWorker;

	protected static final String TABLE_TITLE = "SNT Measurements";
	private final GuiUtils guiUtils;
	private final JScrollPane scrollPane;
	private final JMenuBar menuBar;
	private final JPopupMenu popup;
	private final JMenu swcTypeMenu;
	private final ButtonGroup swcTypeButtonGroup;
	private final ColorMenu colorMenu;
	private final JMenuItem fitVolumeMenuItem;
	private final TreeSearchable searchable;

	/**
	 * Instantiates a new Path Manager {@link JFrame}
	 *
	 * @param plugin
	 *            the the {@link SimpleNeuriteTracer} instance to be associated with
	 *            this Path Manager
	 */
	public PathManagerUI(final SimpleNeuriteTracer plugin) {

		super("Path Manager");
		this.plugin = plugin;
		guiUtils = new GuiUtils(this);
		pathAndFillManager = plugin.getPathAndFillManager();
		pathAndFillManager.addPathAndFillListener(this);

		root = new DefaultMutableTreeNode("All Paths");
		tree = new HelpfulJTree(root);
		tree.setRootVisible(false);
		tree.setVisibleRowCount(25);
		tree.setDoubleBuffered(true);
		tree.addTreeSelectionListener(this);
		scrollPane = new JScrollPane();
		scrollPane.getViewport().add(tree);
		add(scrollPane, BorderLayout.CENTER);

		// Create all the menu items:
		final NoPathActionListener noPathListener = new NoPathActionListener();
		final SinglePathActionListener singlePathListener = new SinglePathActionListener();
		final MultiPathActionListener multiPathListener = new MultiPathActionListener();

		menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		final JMenu editMenu = new JMenu("Edit");
		menuBar.add(editMenu);
		final JMenuItem deleteMitem = new JMenuItem(MultiPathActionListener.DELETE_CMD);
		deleteMitem.addActionListener(multiPathListener);
		editMenu.add(deleteMitem);
		final JMenuItem renameMitem = new JMenuItem(SinglePathActionListener.RENAME_CMD);
		renameMitem.addActionListener(singlePathListener);
		editMenu.add(renameMitem);
		editMenu.addSeparator();
		final JMenuItem primaryMitem = new JMenuItem(SinglePathActionListener.MAKE_PRIMARY_CMD);
		primaryMitem.addActionListener(singlePathListener);
		editMenu.add(primaryMitem);
		JMenuItem jmi = new JMenuItem(SinglePathActionListener.DISCONNECT_CMD);
		jmi.addActionListener(singlePathListener);
		editMenu.add(jmi);
		editMenu.addSeparator();
		jmi = new JMenuItem(MultiPathActionListener.MERGE_CMD);
		jmi.addActionListener(multiPathListener);
		editMenu.add(jmi);
		jmi = new JMenuItem(MultiPathActionListener.DOWNSAMPLE_CMD);
		jmi.addActionListener(multiPathListener);
		editMenu.add(jmi);

		swcTypeMenu = new JMenu("Type");
		menuBar.add(swcTypeMenu);
		swcTypeButtonGroup = new ButtonGroup();
		for (final int type : Path.getSWCtypes()) {
			final JRadioButtonMenuItem rbmi = new JRadioButtonMenuItem(Path.getSWCtypeName(type));
			swcTypeButtonGroup.add(rbmi);
			rbmi.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent e) {
					final HashSet<Path> selectedPaths = getSelectedPaths(true);
					if (selectedPaths.size() == 0) {
						guiUtils.error("There are no traced paths.");
						return;
					}
					if (tree.getSelectionCount() == 0
							&& !guiUtils.getConfirmation("Currently no paths are selected. Change type of all paths?",
									"Apply to All?")) {
						return;
					}
					setSWCType(selectedPaths, type);
					refreshManager(true, false);
				}
			});
			swcTypeMenu.add(rbmi);
		}

		colorMenu = new ColorMenu(MultiPathActionListener.COLORS_MENU);
		menuBar.add(colorMenu);
		colorMenu.addActionListener(multiPathListener);
		jmi = new JMenuItem(MultiPathActionListener.APPLY_SWC_COLORS_CMD);
		jmi.addActionListener(multiPathListener);
		colorMenu.add(jmi);
		jmi = new JMenuItem(MultiPathActionListener.REMOVE_COLOR_CMD);
		jmi.addActionListener(multiPathListener);
		colorMenu.add(jmi);
		colorMenu.addSeparator();
		final JMenu orderTagsMenu = new JMenu("Branching Order");
		jmi = new JMenuItem(MultiPathActionListener.APPEND_ORDER_CMD);
		jmi.addActionListener(multiPathListener);
		orderTagsMenu.add(jmi);
		jmi = new JMenuItem(MultiPathActionListener.REMOVE_ORDER_CMD);
		jmi.addActionListener(multiPathListener);
		orderTagsMenu.add(jmi);
		colorMenu.add(orderTagsMenu);
		final JMenu customTagsMenu = new JMenu("Custom Tags");
		jmi = new JMenuItem(MultiPathActionListener.APPEND_TAG_CMD);
		jmi.addActionListener(multiPathListener);
		customTagsMenu.add(jmi);
		jmi = new JMenuItem(MultiPathActionListener.REMOVE_TAG_CMD);
		jmi.addActionListener(multiPathListener);
		customTagsMenu.add(jmi);
		colorMenu.add(customTagsMenu);

		final JMenu fitMenu = new JMenu("Fit/Fill");
		menuBar.add(fitMenu);
		fitVolumeMenuItem = new JMenuItem("Fit Radii");
		fitVolumeMenuItem.addActionListener(multiPathListener);
		fitMenu.add(fitVolumeMenuItem);
		jmi = new JMenuItem(SinglePathActionListener.EXPLORE_FIT_CMD);
		jmi.addActionListener(singlePathListener);
		fitMenu.add(jmi);
		fitMenu.addSeparator();
		jmi = new JMenuItem(MultiPathActionListener.SPECIFY_FIT_CMD);
		jmi.addActionListener(multiPathListener);
		fitMenu.add(jmi);
		jmi = new JMenuItem(MultiPathActionListener.RESET_FITS);
		jmi.addActionListener(multiPathListener);
		fitMenu.add(jmi);
		fitMenu.addSeparator();
		jmi = new JMenuItem(MultiPathActionListener.FILL_OUT_CMD);
		jmi.addActionListener(multiPathListener);
		fitMenu.add(jmi);

		final JMenu advanced = new JMenu("Tools");
		menuBar.add(advanced);
		jmi = new JMenuItem(MultiPathActionListener.COLORIZE_PATH_CMD);
		jmi.addActionListener(multiPathListener);
		advanced.add(jmi);
		jmi = new JMenuItem(MultiPathActionListener.HISTOGRAM_CMD);
		jmi.addActionListener(multiPathListener);
		advanced.add(jmi);
		jmi = new JMenuItem(MultiPathActionListener.MEASURE_CMD);
		jmi.addActionListener(multiPathListener);
		advanced.add(jmi);
		advanced.addSeparator();
		jmi = new JMenuItem(MultiPathActionListener.CONVERT_TO_ROI_CMD);
		jmi.addActionListener(multiPathListener);
		advanced.add(jmi);
		jmi = new JMenuItem(MultiPathActionListener.CONVERT_TO_SKEL_CMD);
		jmi.addActionListener(multiPathListener);
		advanced.add(jmi);
		advanced.addSeparator();
		jmi = new JMenuItem(MultiPathActionListener.CONVERT_TO_SWC_CMD);
		jmi.addActionListener(multiPathListener);
		advanced.add(jmi);

		// final JMenuItem toggleDnDMenuItem = new JCheckBoxMenuItem(
		// "Allow Hierarchy Edits");
		// toggleDnDMenuItem.setSelected(tree.getDragEnabled());
		// toggleDnDMenuItem.addItemListener(new ItemListener() {
		//
		// // TODO: This is not functional: PathAndFillManager is not aware of any
		// // of these
		// @Override
		// public void itemStateChanged(final ItemEvent e) {
		// tree.setDragEnabled(toggleDnDMenuItem.isSelected() && confirmDnD());
		// if (!tree.getDragEnabled()) displayTmpMsg(
		// "Default behavior restored: Hierarchy is now locked.");
		// }
		//
		// });
		// advanced.add(toggleDnDMenuItem);

		popup = new JPopupMenu();
		final JMenuItem deleteMitem2 = new JMenuItem(MultiPathActionListener.DELETE_CMD);
		deleteMitem2.addActionListener(multiPathListener);
		popup.add(deleteMitem2);
		final JMenuItem renameMitem2 = new JMenuItem(SinglePathActionListener.RENAME_CMD);
		renameMitem2.addActionListener(singlePathListener);
		popup.add(renameMitem2);
		popup.addSeparator();
		JMenuItem pjmi = popup.add(NoPathActionListener.SELECT_NONE_CMD);
		pjmi.addActionListener(noPathListener);
		popup.addSeparator();
		pjmi = popup.add(NoPathActionListener.COLLAPSE_ALL_CMD);
		pjmi.addActionListener(noPathListener);
		pjmi = popup.add(NoPathActionListener.EXPAND_ALL_CMD);
		pjmi.addActionListener(noPathListener);
		final JMenuItem jcbmi = new JCheckBoxMenuItem("Expand Selected Nodes");
		jcbmi.setSelected(tree.getExpandsSelectedPaths());
		jcbmi.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				tree.setExpandsSelectedPaths(jcbmi.isSelected());
				tree.setScrollsOnExpand(jcbmi.isSelected());
			}
		});
		popup.addSeparator();
		popup.add(jcbmi);

		tree.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseReleased(final MouseEvent me) { // Required for Windows
				if (me.isPopupTrigger())
					showPopup(me);
			}

			@Override
			public void mousePressed(final MouseEvent me) {
				if (me.isPopupTrigger()) {
					showPopup(me);
				} else if (tree.getRowForLocation(me.getX(), me.getY()) == -1) {
					tree.clearSelection(); // Deselect when clicking on 'empty space'
				}
			}

		});

		// Search Bar TreeSearchable
		searchable = new TreeSearchable(tree);
		searchable.setCaseSensitive(false);
		searchable.setFromStart(false);
		searchable.setWildcardEnabled(true);
		searchable.setRepeats(true);
		add(bottomPanel(), BorderLayout.PAGE_END);
		pack();
	}

	private void setSWCType(final Set<Path> paths, final int swcType) {
		for (final Path p : paths)
			p.setSWCType(swcType);
	}

	private void resetPathsColor(final Set<Path> paths, final boolean restoreSWCTypeColors) {
		for (final Path p : paths) {
			if (restoreSWCTypeColors)
				p.setColorBySWCtype();
			else
				p.setColor(null);
		}
		refreshManager(true, true);
	}

	private void deletePaths(final Set<Path> pathsToBeDeleted) {
		for (final Path p : pathsToBeDeleted) {
			p.disconnectFromAll();
			pathAndFillManager.deletePath(p);
		}
		refreshManager(false, true);
	}

	/**
	 * Gets the paths currently selected in the Manager's {@link JTree} list.
	 *
	 * @param ifNoneSelectedGetAll
	 *            if true and no paths are currently selected, all Paths in the list
	 *            will be returned
	 * @return the selected paths. Note that children of a Path are not returned if
	 *         unselected.
	 */
	public HashSet<Path> getSelectedPaths(final boolean ifNoneSelectedGetAll) {
		return SwingSafeResult.getResult(new Callable<HashSet<Path>>() {

			@Override
			public HashSet<Path> call() {
				if (ifNoneSelectedGetAll && tree.getSelectionCount() == 0)
					return new HashSet<>(pathAndFillManager.getPathsFiltered());
				final HashSet<Path> result = new HashSet<>();
				final TreePath[] selectedPaths = tree.getSelectionPaths();
				if (selectedPaths == null || selectedPaths.length == 0) {
					return result;
				}
				for (final TreePath tp : selectedPaths) {
					final DefaultMutableTreeNode node = (DefaultMutableTreeNode) (tp.getLastPathComponent());
					if (node != root) {
						final Path p = (Path) node.getUserObject();
						result.add(p);
					}
				}
				return result;
			}
		});
	}

	private void fitPaths(final List<PathFitter> pathsToFit) {
		assert SwingUtilities.isEventDispatchThread();

		if (pathsToFit.isEmpty())
			return; // nothing to fit

		final SNTUI ui = plugin.getUI();
		final int preFittingState = ui.getState();
		ui.changeState(SNTUI.FITTING_PATHS);
		final int numberOfPathsToFit = pathsToFit.size();
		final int processors = Math.min(numberOfPathsToFit, Runtime.getRuntime().availableProcessors());
		final String statusMsg = (processors == 1) ? "Fitting 1 path..."
				: "Fitting " + numberOfPathsToFit + " paths (" + processors + " threads)...";
		ui.showStatus(statusMsg);
		setEnabledCommands(false);
		final JDialog msg = guiUtils.floatingMsg(statusMsg);

		fitWorker = new SwingWorker<Object, Object>() {

			@Override
			protected Object doInBackground() {
				final ExecutorService es = Executors.newFixedThreadPool(processors);
				final FittingProgress progress = new FittingProgress(plugin.statusService, numberOfPathsToFit);
				try {
					for (int i = 0; i < numberOfPathsToFit; ++i) {
						pathsToFit.get(i).setProgressCallback(i, progress);
					}
					for (final Future<Path> future : es.invokeAll(pathsToFit)) {
						pathAndFillManager.addPath(future.get());
					}
				} catch (InterruptedException | ExecutionException | RuntimeException e) {
					msg.dispose();
					guiUtils.error("Unfortunately an Exception occured. See Console for details");
					e.printStackTrace();
				} finally {
					progress.done();
				}
				return null;
			}

			@Override
			protected void done() {
				refreshManager(true, false);
				msg.dispose();
				plugin.changeUIState(preFittingState);
				setEnabledCommands(true);
			}
		};
		fitWorker.execute();
	}

	synchronized protected void cancelFit(final boolean updateUIState) {
		if (fitWorker != null) {
			synchronized (fitWorker) {
				fitWorker.cancel(true);
				if (updateUIState)
					plugin.changeUIState(SNTUI.WAITING_TO_START_PATH);
				fitWorker = null;
			}
		}
	}

	private void exportSelectedPaths(final Set<Path> paths) {

		ArrayList<SWCPoint> swcPoints = null;
		try {
			swcPoints = pathAndFillManager.getSWCFor(paths);
		} catch (final SWCExportException see) {
			guiUtils.error("" + see.getMessage());
			return;
		}

		File saveFile = new File(plugin.getImagePlus().getShortTitle(), ".swc");
		saveFile = guiUtils.saveFile("Export SWC file ...", saveFile, Collections.singletonList(".swc"));

		if (saveFile == null) {
			return; // user pressed cancel
		}

		if (saveFile.exists()
				&& !guiUtils.getConfirmation("The file " + saveFile.getAbsolutePath() + " already exists. Replace it?",
						"Override?"))
			return;

		plugin.statusService.showStatus("Exporting SWC data to " + saveFile.getAbsolutePath());

		try {
			final PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(saveFile), "UTF-8"));
			pathAndFillManager.flushSWCPoints(swcPoints, pw);
			pw.close();
		} catch (final IOException ioe) {
			guiUtils.error("Saving to " + saveFile.getAbsolutePath() + " failed");
			return;
		}
	}

	private void updateCmdsOneSelected(final Path p) {
		assert SwingUtilities.isEventDispatchThread();
		if (p.getUseFitted()) {
			fitVolumeMenuItem.setText("Un-fit Diameters");
		} else {
			fitVolumeMenuItem.setText("Fit Diameters");
			fitVolumeMenuItem.setToolTipText(
					(p.getFitted() == null) ? "Path has never been fitted:\nRadii will be computed for the first time"
							: "Path has already been fitted:\nCached radii will be used");
		}
		colorMenu.selectSWCColor(new SWCColor(p.getColor(), p.getSWCType()));
		selectSWCTypeMenuEntry(p.getSWCType());
	}

	private void updateCmdsManyOrNoneSelected(final Set<Path> selectedPaths) {
		assert SwingUtilities.isEventDispatchThread();

		if (allUsingFittedVersion(selectedPaths)) {
			fitVolumeMenuItem.setText("Un-fit Diameters");
			fitVolumeMenuItem.setToolTipText(null);
		} else {
			fitVolumeMenuItem.setText("Fit Diameters");
			fitVolumeMenuItem.setToolTipText("If fitting has run, cached radii will be applied\n"
					+ " otherwise a new computation will be performed");
		}

		// Update Type & Tags Menu entries only if a real selection exists
		if (tree.getSelectionCount() == 0) {
			colorMenu.selectNone();
			selectSWCTypeMenuEntry(-1);
			return;
		}

		final Path firstPath = selectedPaths.iterator().next();
		final Color firstColor = firstPath.getColor();
		if (!allWithColor(selectedPaths, firstColor)) {
			colorMenu.selectNone();
			return;
		}

		final int type = firstPath.getSWCType();
		if (allWithSWCType(selectedPaths, type)) {
			colorMenu.selectSWCColor(new SWCColor(firstColor, type));
			selectSWCTypeMenuEntry(type);
		} else {
			colorMenu.selectColor(firstColor);
			selectSWCTypeMenuEntry(-1);
		}
	}

	private void selectSWCTypeMenuEntry(final int index) {
		if (index < 0) {
			swcTypeButtonGroup.clearSelection();
			return;
		}
		int idx = 0;
		for (final Component component : swcTypeMenu.getMenuComponents()) {
			if (!(component instanceof JRadioButtonMenuItem))
				continue;
			final JRadioButtonMenuItem mi = (JRadioButtonMenuItem) component;
			mi.setSelected(index == idx++);
		}
	}

	private boolean allWithSWCType(final Set<Path> paths, final int type) {
		if (paths == null || paths.isEmpty())
			return false;
		for (final Path p : paths) {
			if (p.getSWCType() != type)
				return false;
		}
		return true;
	}

	private boolean allWithColor(final Set<Path> paths, final Color color) {
		if (paths == null || paths.isEmpty())
			return false;
		for (final Path p : paths) {
			if (p.getColor() != color)
				return false;
		}
		return true;
	}

	private boolean allUsingFittedVersion(final Set<Path> paths) {
		for (final Path p : paths)
			if (!p.getUseFitted()) {
				return false;
			}
		return true;
	}

	/* (non-Javadoc)
	 * @see javax.swing.event.TreeSelectionListener#valueChanged(javax.swing.event.TreeSelectionEvent)
	 */
	@Override
	public void valueChanged(final TreeSelectionEvent e) {
		assert SwingUtilities.isEventDispatchThread();
		final Set<Path> selectedPaths = getSelectedPaths(true);
		final int selectionCount = tree.getSelectionCount();
		if (selectionCount == 1) {
			updateCmdsOneSelected(selectedPaths.iterator().next());
		} else {
			updateCmdsManyOrNoneSelected(selectedPaths);
		}
		pathAndFillManager.setSelected((selectionCount == 0)?null:(HashSet<Path>) selectedPaths, this);
	}

	private void displayTmpMsg(final String msg) {
		assert SwingUtilities.isEventDispatchThread();
		guiUtils.tempMsg(msg);
	}

	private JPanel bottomPanel() {
		final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
		panel.setBorder(BorderFactory.createEmptyBorder());
		final SearchableBar sBar = new SearchableBar(searchable, true);
		sBar.setShowMatchCount(true);
		sBar.setHighlightAll(false); // TODO: update to 3.6.19 see bugfix
										// https://github.com/jidesoft/jide-oss/commit/149bd6a53846a973dfbb589fffcc82abbc49610b
		sBar.setVisibleButtons(SearchableBar.SHOW_STATUS | SearchableBar.SHOW_HIGHLIGHTS);

		// Make everything more compact and user friendly
		boolean listenerAdded = false;
		for (final Component c : sBar.getComponents()) {
			// ((JComponent) c).setBorder(new EmptyBorder(0, 0, 0, 0));
			if (!listenerAdded && c instanceof JLabel && ((JLabel) c).getText().contains("Find")) {
				((JLabel) c).setToolTipText("Click for Search Tips");
				c.addMouseListener(new MouseAdapter() {

					@Override
					public void mouseClicked(final MouseEvent e) {
						searchHelpMsg();
					}
				});
				listenerAdded = true;
			}
		}
		sBar.setBorder(BorderFactory.createEmptyBorder());
		panel.add(sBar);
		return panel;
	}

	// private boolean confirmDnD() {
	// return guiUtils.getConfirmation(
	// "Enabling this option will allow you to re-link paths through drag-and drop "
	// +
	// "of their respective nodes. Re-organizing paths in such way is useful to " +
	// "proof-edit ill-relashionships but can also render the existing hierarchy " +
	// "of paths meaningless. Please save your work before enabling this option. " +
	// "Enable it now?", "Confirm Hierarchy Edits?");
	// }

	private void searchHelpMsg() {
		final String key = GuiUtils.ctrlKey();
		final String msg = "<HTML><body><div style='width:500;'><ol>"
				+ "<li>Search is case-insensitive. Wildcards <b>?</b> and <b>*</b> are supported.</li>"
				+ "<li>Select the <i>Highlight All</i> button or press " + key
				+ "+A to select all the paths filtered by the search string</li>" + "<li>Press and hold " + key
				+ " while pressing the up/down keys to select multiple filtered paths</li>"
				+ "<li>Press the up/down keys to find the next/previous occurrence of the search string</li>"
				+ "</ol></div></html>";
		guiUtils.centeredMsg(msg, "Searching Paths");
	}

	private void showPopup(final MouseEvent me) {
		assert SwingUtilities.isEventDispatchThread();
		popup.show(me.getComponent(), me.getX(), me.getY());
	}

	private void getExpandedPaths(final HelpfulJTree tree, final TreeModel model, final MutableTreeNode node,
			final HashSet<Path> set) {
		assert SwingUtilities.isEventDispatchThread();
		final int count = model.getChildCount(node);
		for (int i = 0; i < count; i++) {
			final DefaultMutableTreeNode child = (DefaultMutableTreeNode) model.getChild(node, i);
			final Path p = (Path) child.getUserObject();
			if (tree.isExpanded(child.getPath())) {
				set.add(p);
			}
			if (!model.isLeaf(child))
				getExpandedPaths(tree, model, child, set);
		}
	}

	private void setExpandedPaths(final HelpfulJTree tree, final TreeModel model, final MutableTreeNode node,
			final HashSet<Path> set, final Path justAdded) {
		assert SwingUtilities.isEventDispatchThread();
		final int count = model.getChildCount(node);
		for (int i = 0; i < count; i++) {
			final DefaultMutableTreeNode child = (DefaultMutableTreeNode) model.getChild(node, i);
			final Path p = (Path) child.getUserObject();
			if (set.contains(p) || ((justAdded != null) && (justAdded == p))) {
				tree.setExpanded(child.getPath(), true);
			}
			if (!model.isLeaf(child))
				setExpandedPaths(tree, model, child, set, justAdded);
		}

	}

	/* (non-Javadoc)
	 * @see tracing.PathAndFillListener#setSelectedPaths(java.util.HashSet, java.lang.Object)
	 */
	@Override
	public void setSelectedPaths(final HashSet<Path> selectedPaths, final Object source) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				if (source == this)
					return;
				final TreePath[] noTreePaths = {};
				tree.setSelectionPaths(noTreePaths);
				setSelectedPaths(tree, tree.getModel(), root, selectedPaths);
			}
		});
	}

	private void setSelectedPaths(final HelpfulJTree tree, final TreeModel model, final MutableTreeNode node,
			final HashSet<Path> set) {
		assert SwingUtilities.isEventDispatchThread();
		final int count = model.getChildCount(node);
		for (int i = 0; i < count; i++) {
			final DefaultMutableTreeNode child = (DefaultMutableTreeNode) model.getChild(node, i);
			final Path p = (Path) child.getUserObject();
			if (set.contains(p)) {
				tree.setSelected(child.getPath());
			}
			if (!model.isLeaf(child))
				setSelectedPaths(tree, model, child, set);
		}
	}

	/* (non-Javadoc)
	 * @see tracing.PathAndFillListener#setPathList(java.lang.String[], tracing.Path, boolean)
	 */
	@Override
	public void setPathList(final String[] pathList, final Path justAdded, final boolean expandAll) {

		SwingUtilities.invokeLater(() -> {

			// Save the selection state:
			final TreePath[] selectedBefore = tree.getSelectionPaths();
			final HashSet<Path> selectedPathsBefore = new HashSet<>();
			final HashSet<Path> expandedPathsBefore = new HashSet<>();

			if (selectedBefore != null)
				for (int i1 = 0; i1 < selectedBefore.length; ++i1) {
					final TreePath tp = selectedBefore[i1];
					final DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) tp.getLastPathComponent();
					if (dmtn != root) {
						final Path p = (Path) dmtn.getUserObject();
						selectedPathsBefore.add(p);
					}
				}

			// Save the expanded state:
			getExpandedPaths(tree, tree.getModel(), root, expandedPathsBefore);

			/*
			 * Ignore the arguments and get the real path list from the PathAndFillManager:
			 */
			final DefaultMutableTreeNode newRoot = new DefaultMutableTreeNode("All Paths");
			final DefaultTreeModel model = new DefaultTreeModel(newRoot);
			// DefaultTreeModel model = (DefaultTreeModel)tree.getModel();
			final Path[] primaryPaths = pathAndFillManager.getPathsStructured();
			for (int i2 = 0; i2 < primaryPaths.length; ++i2) {
				final Path primaryPath = primaryPaths[i2];
				// Add the primary path if it's not just a fitted version of
				// another:
				if (!primaryPath.isFittedVersionOfAnotherPath())
					addNode(newRoot, primaryPath, model);
			}
			root = newRoot;
			tree.setModel(model);

			model.reload();

			// Set back the expanded state:
			if (expandAll) {
				for (int i3 = 0; i3 < tree.getRowCount(); ++i3)
					tree.expandRow(i3);
			} else
				setExpandedPaths(tree, model, root, expandedPathsBefore, justAdded);

			setSelectedPaths(tree, model, root, selectedPathsBefore);
		});
	}

	private void addNode(final MutableTreeNode parent, final Path childPath, final DefaultTreeModel model) {
		assert SwingUtilities.isEventDispatchThread();
		final MutableTreeNode newNode = new DefaultMutableTreeNode(childPath);
		model.insertNodeInto(newNode, parent, parent.getChildCount());
		for (final Path p : childPath.children)
			addNode(newNode, p, model);
	}

	/* (non-Javadoc)
	 * @see tracing.PathAndFillListener#setFillList(java.lang.String[])
	 */
	@Override
	public void setFillList(final String[] fillList) {
	}

	/** This class defines the JTree hosting traced paths */
	private static class HelpfulJTree extends JTree {

		private static final long serialVersionUID = 1L;

		public HelpfulJTree(final TreeNode root) {
			super(root);
			@SuppressWarnings("serial")
			final DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {

				@Override
				public Component getTreeCellRendererComponent(final JTree tree, final Object value,
						final boolean selected, final boolean expanded, final boolean isLeaf, final int row,
						final boolean focused) {
					final Component c = super.getTreeCellRendererComponent(tree, value, selected, expanded, isLeaf, row,
							focused);
					final TreePath tp = tree.getPathForRow(row);
					if (tp == null)
						return c;
					final DefaultMutableTreeNode node = (DefaultMutableTreeNode) (tp.getLastPathComponent());
					if (node == null || node == root)
						return c;
					final Path p = (Path) node.getUserObject();
					final Color color = p.getColor();
					if (color == null)
						return c;
					if (isLeaf)
						setIcon(new NodeIcon(NodeIcon.EMPTY, color));
					else if (!expanded)
						setIcon(new NodeIcon(NodeIcon.PLUS, color));
					else
						setIcon(new NodeIcon(NodeIcon.MINUS, color));
					return c;
				}
			};
			renderer.setClosedIcon(new NodeIcon(NodeIcon.PLUS));
			renderer.setOpenIcon(new NodeIcon(NodeIcon.MINUS));
			renderer.setLeafIcon(new NodeIcon(NodeIcon.EMPTY));
			setCellRenderer(renderer);
			getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
			// setDragEnabled(true);
			// setDropMode(DropMode.ON_OR_INSERT);
			// setTransferHandler(new TreeTransferHandler());
			setRowHeight(getPreferredRowSize());
		}

		public boolean isExpanded(final Object[] path) {
			assert SwingUtilities.isEventDispatchThread();
			final TreePath tp = new TreePath(path);
			return isExpanded(tp);
		}

		public void setExpanded(final Object[] path, final boolean expanded) {
			assert SwingUtilities.isEventDispatchThread();
			final TreePath tp = new TreePath(path);
			setExpandedState(tp, expanded);
		}

		public void setSelected(final Object[] path) {
			assert SwingUtilities.isEventDispatchThread();
			final TreePath tp = new TreePath(path);
			addSelectionPath(tp);
		}

	}

	/**
	 * This class generates the JTree node icons. Heavily inspired by
	 * http://stackoverflow.com/a/7984734
	 */
	private static class NodeIcon implements Icon {

		private final static int SIZE = getPreferredIconSize();
		private static final char PLUS = '+';
		private static final char MINUS = '-';
		private static final char EMPTY = ' ';
		private final char type;
		private final Color color;

		private NodeIcon(final char type) {
			this.type = type;
			this.color = UIManager.getColor("Tree.background");
		}

		private NodeIcon(final char type, final Color color) {
			this.type = type;
			this.color = color;
		}

		/* see https://stackoverflow.com/a/9780689 */
		private boolean closerToBlack(final Color c) {
			final double y = 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
			return y < 100;
		}

		@Override
		public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
			g.setColor(color);
			g.fillRect(x, y, SIZE - 1, SIZE - 1);
			g.setColor(Color.BLACK);
			g.drawRect(x, y, SIZE - 1, SIZE - 1);
			if (type == EMPTY)
				return;
			g.setColor(closerToBlack(color) ? Color.WHITE : Color.BLACK);
			g.drawLine(x + 2, y + SIZE / 2, x + SIZE - 3, y + SIZE / 2);
			if (type == PLUS) {
				g.drawLine(x + SIZE / 2, y + 2, x + SIZE / 2, y + SIZE - 3);
			}
		}

		@Override
		public int getIconWidth() {
			return SIZE;
		}

		@Override
		public int getIconHeight() {
			return SIZE;
		}

	}

	@SuppressWarnings("unused")
	private static class TreeTransferHandler extends TransferHandler {

		private static final long serialVersionUID = 1L;
		DataFlavor nodesFlavor;
		DataFlavor[] flavors = new DataFlavor[1];
		DefaultMutableTreeNode[] nodesToRemove;

		public TreeTransferHandler() {
			try {
				final String mimeType = DataFlavor.javaJVMLocalObjectMimeType + ";class=\""
						+ javax.swing.tree.DefaultMutableTreeNode[].class.getName() + "\"";
				nodesFlavor = new DataFlavor(mimeType);
				flavors[0] = nodesFlavor;
			} catch (final ClassNotFoundException e) {
				System.out.println("ClassNotFound: " + e.getMessage());
			}
		}

		@Override
		public boolean canImport(final TransferHandler.TransferSupport support) {
			if (!support.isDrop()) {
				return false;
			}
			support.setShowDropLocation(true);
			if (!support.isDataFlavorSupported(nodesFlavor)) {
				return false;
			}
			// Do not allow a drop on the drag source selections.
			final JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
			final JTree tree = (JTree) support.getComponent();
			final int dropRow = tree.getRowForPath(dl.getPath());
			final int[] selRows = tree.getSelectionRows();
			for (int i = 0; i < selRows.length; i++) {
				if (selRows[i] == dropRow) {
					return false;
				}
			}
			// Do not allow MOVE-action drops if a non-leaf node is
			// selected unless all of its children are also selected.
			final int action = support.getDropAction();
			if (action == MOVE) {
				return haveCompleteNode(tree);
			}
			// Do not allow a non-leaf node to be copied to a level
			// which is less than its source level.
			final TreePath dest = dl.getPath();
			final DefaultMutableTreeNode target = (DefaultMutableTreeNode) dest.getLastPathComponent();
			final TreePath path = tree.getPathForRow(selRows[0]);
			final DefaultMutableTreeNode firstNode = (DefaultMutableTreeNode) path.getLastPathComponent();
			if (firstNode.getChildCount() > 0 && target.getLevel() < firstNode.getLevel()) {
				return false;
			}
			return true;
		}

		private boolean haveCompleteNode(final JTree tree) {
			final int[] selRows = tree.getSelectionRows();
			TreePath path = tree.getPathForRow(selRows[0]);
			final DefaultMutableTreeNode first = (DefaultMutableTreeNode) path.getLastPathComponent();
			final int childCount = first.getChildCount();
			// first has children and no children are selected.
			if (childCount > 0 && selRows.length == 1)
				return false;
			// first may have children.
			for (int i = 1; i < selRows.length; i++) {
				path = tree.getPathForRow(selRows[i]);
				final DefaultMutableTreeNode next = (DefaultMutableTreeNode) path.getLastPathComponent();
				if (first.isNodeChild(next)) {
					// Found a child of first.
					if (childCount > selRows.length - 1) {
						// Not all children of first are selected.
						return false;
					}
				}
			}
			return true;
		}

		@Override
		protected Transferable createTransferable(final JComponent c) {
			final JTree tree = (JTree) c;
			final TreePath[] paths = tree.getSelectionPaths();
			if (paths != null) {
				// Make up a node array of copies for transfer and
				// another for/of the nodes that will be removed in
				// exportDone after a successful drop.
				final List<DefaultMutableTreeNode> copies = new ArrayList<>();
				final List<DefaultMutableTreeNode> toRemove = new ArrayList<>();
				final DefaultMutableTreeNode node = (DefaultMutableTreeNode) paths[0].getLastPathComponent();
				final DefaultMutableTreeNode copy = copy(node);
				copies.add(copy);
				toRemove.add(node);
				for (int i = 1; i < paths.length; i++) {
					final DefaultMutableTreeNode next = (DefaultMutableTreeNode) paths[i].getLastPathComponent();
					// Do not allow higher level nodes to be added to list.
					if (next.getLevel() < node.getLevel()) {
						break;
					} else if (next.getLevel() > node.getLevel()) { // child node
						copy.add(copy(next));
						// node already contains child
					} else { // sibling
						copies.add(copy(next));
						toRemove.add(next);
					}
				}
				final DefaultMutableTreeNode[] nodes = copies.toArray(new DefaultMutableTreeNode[copies.size()]);
				nodesToRemove = toRemove.toArray(new DefaultMutableTreeNode[toRemove.size()]);
				return new NodesTransferable(nodes);
			}
			return null;
		}

		/** Defensive copy used in createTransferable. */
		private DefaultMutableTreeNode copy(final TreeNode node) {
			final DefaultMutableTreeNode n = (DefaultMutableTreeNode) node;
			return (DefaultMutableTreeNode) n.clone();
		}

		@Override
		protected void exportDone(final JComponent source, final Transferable data, final int action) {
			if ((action & MOVE) == MOVE) {
				final JTree tree = (JTree) source;
				final DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
				// Remove nodes saved in nodesToRemove in createTransferable.
				for (int i = 0; i < nodesToRemove.length; i++) {
					model.removeNodeFromParent(nodesToRemove[i]);
				}
			}
		}

		@Override
		public int getSourceActions(final JComponent c) {
			return COPY_OR_MOVE;
		}

		@Override
		public boolean importData(final TransferHandler.TransferSupport support) {
			if (!canImport(support)) {
				return false;
			}
			// Extract transfer data.
			DefaultMutableTreeNode[] nodes = null;
			try {
				final Transferable t = support.getTransferable();
				nodes = (DefaultMutableTreeNode[]) t.getTransferData(nodesFlavor);
			} catch (final UnsupportedFlavorException ufe) {
				System.out.println("UnsupportedFlavor: " + ufe.getMessage());
			} catch (final java.io.IOException ioe) {
				System.out.println("I/O error: " + ioe.getMessage());
			}
			// Get drop location info.
			final JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
			final int childIndex = dl.getChildIndex();
			final TreePath dest = dl.getPath();
			final DefaultMutableTreeNode parent = (DefaultMutableTreeNode) dest.getLastPathComponent();
			final JTree tree = (JTree) support.getComponent();
			final DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
			// Configure for drop mode.
			int index = childIndex; // DropMode.INSERT
			if (childIndex == -1) { // DropMode.ON
				index = parent.getChildCount();
			}
			// Add data to model.
			for (int i = 0; i < nodes.length; i++) {
				model.insertNodeInto(nodes[i], parent, index++);
			}
			return true;
		}

		@Override
		public String toString() {
			return getClass().getName();
		}

		public class NodesTransferable implements Transferable {

			DefaultMutableTreeNode[] nodes;

			public NodesTransferable(final DefaultMutableTreeNode[] nodes) {
				this.nodes = nodes;
			}

			@Override
			public Object getTransferData(final DataFlavor flavor) throws UnsupportedFlavorException {
				if (!isDataFlavorSupported(flavor))
					throw new UnsupportedFlavorException(flavor);
				return nodes;
			}

			@Override
			public DataFlavor[] getTransferDataFlavors() {
				return flavors;
			}

			@Override
			public boolean isDataFlavorSupported(final DataFlavor flavor) {
				return nodesFlavor.equals(flavor);
			}
		}
	}

	private static int getPreferredRowSize() {
		final JTree tree = new JTree();
		return tree.getFontMetrics(tree.getFont()).getHeight();
	}

	private static int getPreferredIconSize() {
		final JTree tree = new JTree();
		final int size = tree.getFontMetrics(tree.getFont()).getAscent();
		return (size % 2 == 0) ? size - 1 : size;
	}

	private void setEnabledCommands(final boolean enabled) {
		assert SwingUtilities.isEventDispatchThread();
		tree.setEnabled(enabled);
		menuBar.setEnabled(enabled);
	}

	private void exploreFit(final Path p) {
		assert SwingUtilities.isEventDispatchThread();

		// Announce computation
		final SNTUI ui = plugin.getUI();
		final String statusMsg = "Fitting " + p.toString();
		ui.showStatus(statusMsg);
		setEnabledCommands(false);

		// Improve browsability of path, while updating the GUI
		if (!plugin.showOnlySelectedPaths)
			ui.togglePathsChoice();
		plugin.enableEditMode(true);
		plugin.setEditingPath(p);
		final String text = "Once opened, you can peruse the fit by "
				+ "navigating the 'Normal Plane' image. Nodes are "
				+ "automatically synchronized with tracing canvas(es).";
		final JDialog msg = guiUtils.floatingMsg(text);

		new Thread(() -> {

			// No image is displayed if run on EDT
			final SwingWorker<?, ?> worker = new SwingWorker<Object, Object>() {

				@Override
				protected Object doInBackground() throws Exception {

					try {
						// Compute verbose fit
						final PathFitter fitter = new PathFitter(plugin, p, true);
						final ExecutorService executor = Executors.newSingleThreadExecutor();
						final Future<Path> future = executor.submit(fitter);
						final Path result = future.get();
						pathAndFillManager.addPath(result);
						refreshManager(true, false);
					} catch (InterruptedException | ExecutionException | RuntimeException e) {
						msg.dispose();
						guiUtils.error("Unfortunately an exception occured. See Console for details");
						e.printStackTrace();
					}
					return null;
				}

				@Override
				protected void done() {
					// It may take longer to read the text than to compute
					// Normal Views: we will not call msg.dispose();
					GuiUtils.setAutoDismiss(msg);
					setEnabledCommands(true);
				}
			};
			worker.execute();
		}).start();
	}

	private void refreshManager(final boolean refreshCmds, final boolean refreshViewers) {
		pathAndFillManager.resetListeners(null);
		if (refreshViewers) plugin.updateAllViewers();
		if (!refreshCmds)
			return;
		final Set<Path> selectedPaths = getSelectedPaths(true);
		if (tree.getSelectionCount() == 1)
			updateCmdsOneSelected(selectedPaths.iterator().next());
		else
			updateCmdsManyOrNoneSelected(selectedPaths);
	}

	/**
	 * Refreshes viewers and rebuilds Menus to reflect new contents in the Path
	 * Manager.
	 */
	public void update() {
		refreshManager(true, true);
	}

	protected void closeTable() {
		final Display<?> display = plugin.getContext().getService(DisplayService.class).getDisplay(TABLE_TITLE);
		if (display != null && display.isDisplaying(table))
			display.close();
	}

	protected DefaultGenericTable getTable() {
		if (table == null)
			table = new DefaultGenericTable();
		// we will assume that immediately after being retrieved,
		// the table will contain unsaved data. //FIXME: sloppy
		tableSaved = false;
		return table;
	}

	protected boolean measurementsUnsaved() {
		return validTableMeasurements() && !tableSaved;
	}

	private boolean validTableMeasurements() {
		return table != null && table.getRowCount() > 0 && table.getColumnCount() > 0;
	}

	private void saveTable(final File outputFile) throws IOException {
		final String sep = ",";
		final PrintWriter pw = new PrintWriter(
				new OutputStreamWriter(new FileOutputStream(outputFile.getAbsolutePath()), "UTF-8"));
		final int columns = table.getColumnCount();
		final int rows = table.getRowCount();

		// Print a column header to hold row headers
		SNT.csvQuoteAndPrint(pw, "Description");
		pw.print(sep);
		for (int col = 0; col < columns; ++col) {
			SNT.csvQuoteAndPrint(pw, table.getColumnHeader(col));
			if (col < (columns - 1))
				pw.print(sep);
		}
		pw.print("\r\n");
		for (int row = 0; row < rows; row++) {
			SNT.csvQuoteAndPrint(pw, table.getRowHeader(row));
			pw.print(sep);
			for (int col = 0; col < columns; col++) {
				SNT.csvQuoteAndPrint(pw, table.get(col, row));
				if (col < (columns - 1))
					pw.print(sep);
			}
			pw.print("\r\n");
		}
		pw.close();
		tableSaved = true;
	}

	protected void saveTable() {
		if (!validTableMeasurements()) {
			plugin.error("There are no measurements to save.");
			return;
		}
		File saveFile = new File("/home/tferr/Desktop/");
		saveFile = guiUtils.saveFile("Save SNT Measurements...", saveFile, Collections.singletonList(".csv"));
		if (saveFile == null)
			return; // user pressed cancel

		if (saveFile.exists()
				&& !guiUtils.getConfirmation("The file " + saveFile.getAbsolutePath() + " already exists. Replace it?",
						"Override?")) {
			return;
		}
		plugin.getUI().showStatus("Exporting Measurements..");
		try {
			saveTable(saveFile);
		} catch (final IOException e) {
			plugin.error("Unfortunately an Exception occured. See Console for details");
			e.printStackTrace();
		}
	}

	private void removeOrderTags(final HashSet<Path> selectedPaths) {
		for (final Path p : selectedPaths) {
			p.setName(p.getName().replaceAll("\\<Order \\d+\\>", ""));
		}
	}

	private void removeAllOrderTags() {
		tree.clearSelection();
		removeOrderTags(getSelectedPaths(true));
	}

	/** ActionListener for commands that do not deal with paths */
	private class NoPathActionListener implements ActionListener {

		private final static String EXPAND_ALL_CMD = "Expand All";
		private final static String COLLAPSE_ALL_CMD = "Collapse All";
		private final static String SELECT_NONE_CMD = "Deselect / Select All";

		@Override
		public void actionPerformed(final ActionEvent e) {

			if (e.getActionCommand().equals(SELECT_NONE_CMD)) {
				tree.clearSelection();
				return;
			} else if (e.getActionCommand().equals(EXPAND_ALL_CMD)) {
				for (int i = 0; i < tree.getRowCount(); i++)
					tree.expandRow(i);
				return;
			} else if (e.getActionCommand().equals(COLLAPSE_ALL_CMD)) {
				for (int i = 0; i < tree.getRowCount(); i++)
					tree.collapseRow(i);
				return;
			} else
				SNT.error("Unexpectedly got an event from an unknown source: " + e);
		}
	}

	/** ActionListener for commands operating exclusively on a single path */
	private class SinglePathActionListener implements ActionListener {

		private final static String RENAME_CMD = "Rename...";
		private final static String MAKE_PRIMARY_CMD = "Make Primary";
		private final static String DISCONNECT_CMD = "Disconnect...";
		private final static String EXPLORE_FIT_CMD = "Explore Fit";

		@Override
		public void actionPerformed(final ActionEvent e) {

			// Process nothing without a single path selection
			final Set<Path> selectedPaths = getSelectedPaths(false);
			if (selectedPaths.size() != 1) {
				displayTmpMsg("You must have exactly one path selected.");
				return;
			}
			final Path p = selectedPaths.iterator().next();

			if (e.getActionCommand().equals(RENAME_CMD)) {
				final String s = guiUtils.getString("Rename this path to (clear to reset name):", "Rename Path",
						p.getName());
				if (s == null)
					return; // user pressed cancel
				synchronized (pathAndFillManager) {
					if (s.trim().isEmpty()) {
						p.setDefaultName();
					} else if (pathAndFillManager.getPathFromName(s, false) != null) {
						displayTmpMsg("There is already a path named:\n('" + s + "')");
						return;
					} else {// Otherwise this is OK, change the name:
						p.setName(s);
					}
					refreshManager(false, false);
				}
				return;
			} else if (e.getActionCommand().equals(MAKE_PRIMARY_CMD)) {
				final HashSet<Path> pathsExplored = new HashSet<>();
				p.setIsPrimary(true);
				pathsExplored.add(p);
				p.unsetPrimaryForConnected(pathsExplored);
				removeAllOrderTags();
				refreshManager(false, false);
				return;

			} else if (e.getActionCommand().equals(DISCONNECT_CMD)) {
				if (!guiUtils.getConfirmation("Disconnect \"" + p.toString() + "\" from all it connections?",
						"Confirm Disconnect"))
					return;
				p.disconnectFromAll();
				removeAllOrderTags();
				refreshManager(false, false);
				return;

			} else if (e.getActionCommand().equals(EXPLORE_FIT_CMD)) {
				if (plugin.getImagePlus() == null) {
					displayTmpMsg("Tracing image is not available. Radii cannot be calculated.");
					return;
				}
				if (p.getFitted() != null) {
					final boolean cf = guiUtils.getConfirmation(
							p.toString() + " has already been fitted. Recalculate radii?", "Recalculate?");
					if (!cf)
						return;
					p.setUseFitted(false);
					p.setFitted(null);
					refreshManager(true, false);
				}
				if (!plugin.editModeAllowed(true))
					return;
				exploreFit(p);
				return;
			}

			SNT.error("Unexpectedly got an event from an unknown source: " + e);
		}
	}

	/** ActionListener for commands that can operate on multiple paths */
	private class MultiPathActionListener implements ActionListener {

		private final static String COLORS_MENU = "Tags";
		private final static String DELETE_CMD = "Delete...";
		private final static String MERGE_CMD = "Merge...";
		private final static String DOWNSAMPLE_CMD = "Douglas–Peucker Downsampling...";
		private final static String APPLY_SWC_COLORS_CMD = "Apply SWC-Type Colors";
		private final static String REMOVE_COLOR_CMD = "Remove Color Tags";
		private final static String APPEND_ORDER_CMD = "Append Order Tag";
		private final static String REMOVE_ORDER_CMD = "Remove Order Tag";
		private final static String APPEND_TAG_CMD = "Append Custom Tag(s)";
		private final static String REMOVE_TAG_CMD = "Remove Custom Tag(s)";
		private static final String FILL_OUT_CMD = "Fill Out...";
		private static final String RESET_FITS = "Reset Fits...";
		private final static String SPECIFY_FIT_CMD = "Specify Radius...";
		private final static String MEASURE_CMD = "Measure";
		private final static String CONVERT_TO_ROI_CMD = "Send to ROI Manager...";
		private final static String COLORIZE_PATH_CMD = "Color Coding...";
		private final static String HISTOGRAM_CMD = "Distribution Analysis...";
		private final static String CONVERT_TO_SKEL_CMD = "Skeletonize...";
		private final static String CONVERT_TO_SWC_CMD = "Save as SWC...";

		@Override
		public void actionPerformed(final ActionEvent e) {

			final String cmd = e.getActionCommand();
			final HashSet<Path> selectedPaths = getSelectedPaths(true);
			final int n = selectedPaths.size();

			if (n == 0) {
				guiUtils.error("There are no traced paths.");
				return;
			}

			// If no path is selected, remind user that action applies to all paths
			final boolean assumeAll = tree.getSelectionCount() == 0;
			// final boolean assumeAll = noSelection && guiUtils.getConfirmation("Currently
			// no paths are selected. Apply command to all paths?", cmd);
			// if (noSelection && !assumeAll) return;

			// Case 1: Non-destructive commands that do not require confirmation
			if (REMOVE_COLOR_CMD.equals(cmd)) {
				resetPathsColor(selectedPaths, false);

			} else if (APPLY_SWC_COLORS_CMD.equals(cmd)) {
				resetPathsColor(selectedPaths, true);

			} else if (COLORS_MENU.equals(cmd)) {
				final SWCColor swcColor = colorMenu.getSelectedSWCColor();
				if (swcColor.isTypeDefined())
					setSWCType(selectedPaths, swcColor.type());
				for (final Path p : selectedPaths)
					p.setColor(swcColor.color());
				refreshManager(true, true);
				return;

			} else if (MEASURE_CMD.equals(cmd)) {
				try {
					final TreeAnalyzer ta = new TreeAnalyzer(new Tree(selectedPaths));
					ta.setContext(plugin.getContext());
					if (ta.getParsedTree().isEmpty()) {
						guiUtils.error("None of the selected paths could be measured.");
						return;
					}
					ta.setTable(getTable(), TABLE_TITLE);
					ta.summarize(getDescription(selectedPaths), true);
					ta.updateAndDisplayTable();
					return;
				} catch (final IllegalArgumentException ignored) {
					guiUtils.error("Selected paths do not fullfill requirements for measurements");
				}

			} else if (CONVERT_TO_ROI_CMD.equals(cmd)) {
				final Map<String, Object> input = new HashMap<>();
				input.put("tree", new Tree(selectedPaths));
				final CommandService cmdService = plugin.getContext().getService(CommandService.class);
				cmdService.run(ROIExporterCmd.class, true, input);

			} else if (COLORIZE_PATH_CMD.equals(cmd)) {
				final Map<String, Object> input = new HashMap<>();
				input.put("tree", new Tree(selectedPaths));
				input.put("manager", getInstance());
				final CommandService cmdService = plugin.getContext().getService(CommandService.class);
				cmdService.run(TreeColorizerCmd.class, true, input);

			} else if (HISTOGRAM_CMD.equals(cmd)) {
				final Map<String, Object> input = new HashMap<>();
				input.put("tree", new Tree(selectedPaths));
				input.put("title", "SNT: Hist. " + getDescription(selectedPaths));
				final CommandService cmdService = plugin.getContext().getService(CommandService.class);
				cmdService.run(DistributionCmd.class, true, input);

			} else if (APPEND_ORDER_CMD.equals(cmd)) {
				for (final Path p : selectedPaths) {
					final String name = p.getName();
					if (!name.contains("<Order "))
						p.setName(name + "<Order " + p.getOrder() + ">");
				}
				refreshManager(false, false);

			} else if (REMOVE_ORDER_CMD.equals(cmd)) {
				removeOrderTags(selectedPaths);
				refreshManager(false, false);

			} else if (APPEND_TAG_CMD.equals(cmd)) {
				final String tags = guiUtils.getString("Enter one or more tags (space or comma-separated list):",
						"Append Tags", "");
				if (tags == null || tags.trim().isEmpty())
					return; // user pressed cancel or type nothing in the prompt
				for (final Path p : selectedPaths) {
					p.setName(p.getName() + "<Tag " + tags + ">");
				}
				refreshManager(false, false);

			} else if (REMOVE_TAG_CMD.equals(cmd)) {
				for (final Path p : selectedPaths) {
					p.setName(p.getName().replaceAll("\\<Tag .+\\>", ""));
				}
				refreshManager(false, false);

			} else if (CONVERT_TO_SKEL_CMD.equals(cmd)) {
				new SkeletonConverter(plugin).runGui();
				return;

			} else if (CONVERT_TO_SWC_CMD.equals(cmd)) {
				exportSelectedPaths(selectedPaths);
				return;

			} else if (FILL_OUT_CMD.equals(cmd)) {
				plugin.startFillingPaths(selectedPaths);
				return;

			} else if (SPECIFY_FIT_CMD.equals(e.getActionCommand())) {

				if (allUsingFittedVersion(selectedPaths)) {
					guiUtils.error("This command only applies to unfitted paths.");
					return;
				}
				final double rad = 2 * plugin.getMinimumSeparation();
				final Double userRad = guiUtils.getDouble("<HTML><body><div style='width:" + Math.min(getWidth(), 500)
						+ ";'>" + "Please specify a constant radius for all the nodes "
						+ "of selected path(s). This setting only applies to unfitted "
						+ "paths and <b>overrides</b> any existing values.", "Assign Constant Diameter", rad);
				if (userRad == null) {
					return; // user pressed cancel
				}
				if (Double.isNaN(userRad) || userRad < 0) {
					guiUtils.error("Invalid diameter value.");
					return;
				}
				if (userRad == 0d && !guiUtils.getConfirmation("Discard thickness information from selected paths?",
						"Confirm Removal of Diameters")) {
					return;
				}
				selectedPaths.parallelStream().forEach(p -> {
					if (!p.isFittedVersionOfAnotherPath()) p.setRadii(userRad);
				});
				guiUtils.tempMsg("Command finished. Fitted path(s) ignored.");
				plugin.updateAllViewers();
				return;
			}

			// Case 2: Commands that require some sort of confirmation
			else if (DELETE_CMD.equals(cmd)) {
				if (guiUtils.getConfirmation((assumeAll) ? "Are you really sure you want to delete everything?"
						: "Delete the selected " + n + " paths?", "Confirm Deletion?"))
					deletePaths(selectedPaths);
				return;
			} else if (MERGE_CMD.equals(cmd)) {
				if (n == 1) {
					displayTmpMsg("You must have at least two paths selected.");
					return;
				}
				final Path refPath = selectedPaths.iterator().next();
				if (refPath.getEndJoins() != null) {
					guiUtils.error("The first path in the selection cannot have an end-point junction.",
							"Invalid Merge Selection");
					return;
				}
				if (!guiUtils.getConfirmation(
						"Merge " + n + " selected paths? (this destructive operation cannot be undone!)",
						"Confirm merge?")) {
					return;
				}
				final HashSet<Path> pathsToMerge = new HashSet<>();
				for (final Path p : selectedPaths) {
					if (refPath.equals(p) || refPath.somehowJoins.contains(p) || p.somehowJoins.contains(refPath))
						continue;
					pathsToMerge.add(p);
				}
				if (pathsToMerge.size() < n - 1
						&& !guiUtils.getConfirmation(
								"Some of the selected paths are connected and cannot be merged. "
										+ "Proceed with the merge of the " + pathsToMerge.size()
										+ " disconnected path(s) in the selection?",
								"Only Disconnected Paths Can Be Merged")) {
					return;
				}
				for (final Path p : pathsToMerge) {
					refPath.add(p);
					pathAndFillManager.deletePath(p);
				}
				removeAllOrderTags();
				refreshManager(true, true);

			} else if (DOWNSAMPLE_CMD.equals(cmd)) {
				final double minSep = plugin.getMinimumSeparation();
				final Double userMaxDeviation = guiUtils.getDouble("<HTML><body><div style='width:500;'>"
						+ "Please specify the maximum permitted distance between nodes:<ul>"
						+ "<li>This destructive operation cannot be undone!</li>"
						+ "<li>Paths can only be downsampled: Smaller inter-node distances will not be interpolated</li>"
						+ "<li>Currently, the smallest voxel dimension is " + SNT.formatDouble(minSep, 3)
						+ plugin.spacing_units + "</li>", "Downsampling: " + n + " Selected Path(s)", 2 * minSep);
				if (userMaxDeviation == null)
					return; // user pressed cancel

				final double maxDeviation = userMaxDeviation.doubleValue();
				if (Double.isNaN(maxDeviation) || maxDeviation <= 0) {
					guiUtils.error("The maximum permitted distance must be a postive number", "Invalid Input");
					return;
				}
				for (final Path p : selectedPaths) {
					Path pathToUse = p;
					if (p.getUseFitted()) {
						pathToUse = p.getFitted();
					}
					pathToUse.downsample(maxDeviation);
				}
				// Make sure that the 3D viewer and the stacks are redrawn:
				plugin.updateAllViewers();
			} else if (RESET_FITS.equals(cmd)) {
				if (!guiUtils.getConfirmation("Discard fitted diameters?", "Confirm Reset?"))
					return;
				for (final Path p : selectedPaths) {
					p.setUseFitted(false);
					p.setFitted(null);
				}
				refreshManager(true, false);
				return;
			} else if (e.getSource().equals(fitVolumeMenuItem)) {

				// this MenuItem is a toggle: check if it is set for 'unfitting'
				if (fitVolumeMenuItem.getText().contains("Un-fit")) {
					for (final Path p : selectedPaths)
						p.setUseFitted(false);
					refreshManager(true, false);
					return;
				}

				final int currentState = plugin.getUI().getState();
				final boolean imagenotAvailable = currentState == SNTUI.IMAGE_CLOSED
						|| currentState == SNTUI.ANALYSIS_MODE;
				final ArrayList<PathFitter> pathsToFit = new ArrayList<>();
				int skippedFits = 0;

				for (final Path p : selectedPaths) {

					// If the fitted version is already being used. Do nothing
					if (p.getUseFitted()) {
						continue;
					}

					// A fitted version does not exist
					else if (p.getFitted() == null) {
						if (imagenotAvailable) {
							// Keep a tally of how many computations we are skipping
							skippedFits++;
						} else {
							// Prepare for computation
							final PathFitter pathFitter = new PathFitter(plugin, p, false);
							pathsToFit.add(pathFitter);
						}
					}

					// Just use the existing fitted version:
					else {
						p.setUseFitted(true);
					}
				}

				if (pathsToFit.size() > 0) {
					fitPaths(pathsToFit); // call refreshManager
					if (skippedFits > 0) {
						guiUtils.centeredMsg("Since image is not available, " + skippedFits + "/" + selectedPaths.size()
								+ " fits could not be computed", "Image Not Available");
					}
				} else {
					refreshManager(true, false);
				}

				return;

			} else {
				SNT.error("Unexpectedly got an event from an unknown source: " + e);
				return;
			}
		}

		private boolean allPathNamesContain(final HashSet<Path> selectedPaths, final String string) {
			if (string == null || string.trim().isEmpty())
				return false;
			for (final Path p : selectedPaths) {
				if (!p.getName().contains(string))
					return false;
			}
			return true;
		}

		private String getDescription(final HashSet<Path> selectedPaths) {
			String description;
			final int n = selectedPaths.size();
			if (n == pathAndFillManager.getPathsFiltered().size()) {
				description = "All Paths";
			} else if (n == 1) {
				description = selectedPaths.iterator().next().getName();
			} else if (n > 1 && allPathNamesContain(selectedPaths, searchable.getSearchingText())) {
				description = "Filter [" + searchable.getSearchingText() + "]";
			} else {
				description = "Path IDs [" + Path.pathsToIDListString(new ArrayList<>(selectedPaths)) + "]";
			}
			return description;
		}
	}

	private PathManagerUI getInstance() {
		return this;
	}

	/** IDE debug method */
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		final ImagePlus imp = new ImagePlus();
		final SimpleNeuriteTracer snt = new SimpleNeuriteTracer(ij.context(), imp);
		final PathManagerUI pm = new PathManagerUI(snt);
		pm.setVisible(true);
	}

}
