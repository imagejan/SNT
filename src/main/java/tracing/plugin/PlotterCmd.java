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

package tracing.plugin;

import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;

import net.imagej.ImageJ;

import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Interactive;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.util.ColorRGB;
import org.scijava.widget.NumberWidget;

import ij.measure.Calibration;
import tracing.Path;
import tracing.Tree;
import tracing.analysis.TreePlot;
import tracing.util.PointInImage;

/**
 * Command for interactively plotting trees using {@link TreePlot}
 *
 * @author Tiago Ferreira
 */
@Plugin(type = DynamicCommand.class, visible = false, label = "Interactive Traces Plot")
public class PlotterCmd extends DynamicCommand implements Interactive {

	@Parameter(required = false, label = "X rotation", description = "Rotation angle in degrees", min = "0", max = "360", stepSize = "10", style = NumberWidget.SCROLL_BAR_STYLE, callback = "currentXangleChanged")
	private int angleX = 0;

	@Parameter(required = false, label = "Y rotation", description = "Rotation angle in degrees", min = "0", max = "360", stepSize = "10", style = NumberWidget.SCROLL_BAR_STYLE, callback = "currentYangleChanged")
	private int angleY = 0;

	@Parameter(required = false, label = "Z rotation", description = "Rotation angle in degrees", min = "0", max = "360", stepSize = "10", style = NumberWidget.SCROLL_BAR_STYLE, callback = "currentZangleChanged")
	private int angleZ = 0;

	@Parameter(required = false, label = "Actions", choices = { ACTION_NONE, ACTION_FLIP_H, ACTION_FLIP_V, ACTION_RESET,
			ACTION_SNAPSHOT }, callback = "runAction")
	private String actionChoice;

	@Parameter(required = true)
	private Tree tree;

	@Parameter(required = false)
	private String title;

	private static final String ACTION_NONE = "Choose...";
	private static final String ACTION_RESET = "Reset rotation";
	private static final String ACTION_FLIP_H = "Flip horizontally";
	private static final String ACTION_FLIP_V = "Flip vertically";
	private static final String ACTION_SNAPSHOT = "Take snapshot";
	private static final ColorRGB DEF_COLOR = new ColorRGB("black");
	private TreePlot plot;
	private JFreeChart chart;
	private ChartFrame frame;
	private Tree plottingTree;
	private Tree snapshotTree;
	private int previousXangle = 0;
	private int previousYangle = 0;
	private int previousZangle = 0;

	@Override
	public void run() {

		if (tree == null || tree.isEmpty())
			cancel("No paths to plot");

		// Tree rotation occurs in place so we'll copy plotting coordinates
		// to a new Tree. To avoid rotation lags we'll keep it monochrome,
		// We'll store input colors to be restored by the 'snapshot' action
		plottingTree = new Tree();
		snapshotTree = new Tree();
		for (final Path p : tree.getPaths()) {
			Path pathToPlot;
			if (p.getUseFitted())
				pathToPlot = p.getFitted();
			else
				pathToPlot = p;
			final Calibration cal = pathToPlot.getCalibration();
			final Path dup = new Path(cal.pixelWidth, cal.pixelHeight, cal.pixelDepth, cal.getUnit());
			for (int i = 0; i < pathToPlot.size(); i++) {
				final PointInImage pim = pathToPlot.getPointInImage(i);
				dup.addPointDouble(pim.x, pim.y, pim.z);
			}
			dup.setSWCType(pathToPlot.getSWCType());
			plottingTree.addPath(dup);
			snapshotTree.addPath(pathToPlot);
		}
		buildPlot();
		chart = plot.getChart();
		frame = new ChartFrame(title, chart);
		frame.setPreferredSize(new Dimension(500, 500));
		frame.pack();
		frame.setVisible(true);
	}

	private void buildPlot() {
		plot = new TreePlot(context());
		plot.setDefaultColor(DEF_COLOR);
		plot.addTree(plottingTree);
	}

	private void updatePlot() {
		buildPlot();
		chart.getXYPlot().setDataset(plot.getChart().getXYPlot().getDataset());
		frame.setVisible(true); // re-open frame if it has been closed
		// frame.toFront();
	}

	private void resetRotation() {
		plottingTree.rotate(Tree.X_AXIS, -angleX);
		plottingTree.rotate(Tree.Y_AXIS, -angleY);
		plottingTree.rotate(Tree.Z_AXIS, -angleZ);
		updatePlot();
		angleX = 0;
		angleY = 0;
		angleZ = 0;
		previousXangle = 0;
		previousYangle = 0;
		previousZangle = 0;
	}

	@SuppressWarnings("unused")
	private void currentXangleChanged() {
		plottingTree.rotate(Tree.X_AXIS, angleX - previousXangle);
		previousXangle = angleX;
		updatePlot();
	}

	@SuppressWarnings("unused")
	private void currentYangleChanged() {
		plottingTree.rotate(Tree.Y_AXIS, angleY - previousYangle);
		previousYangle = angleY;
		updatePlot();
	}

	@SuppressWarnings("unused")
	private void currentZangleChanged() {
		plottingTree.rotate(Tree.Z_AXIS, angleZ - previousZangle);
		previousZangle = angleZ;
		updatePlot();
	}

	private int getBoundedAngle(int angle) {
		if (angle >= 360)
			angle -= 360;
		if (angle < 0)
			angle += 360;
		return angle;
	}

	@SuppressWarnings("unused")
	private void runAction() {
		switch (actionChoice) {
		case ACTION_NONE:
			return;
		case ACTION_RESET:
			resetRotation();
			actionChoice = ACTION_NONE;
			return;
		case ACTION_SNAPSHOT:
			snapshot();
			actionChoice = ACTION_NONE;
			return;
		case ACTION_FLIP_V:
			angleX = getBoundedAngle(180 + angleX);
			plottingTree.rotate(Tree.X_AXIS, angleX);
			previousXangle = angleX;
			updatePlot();
			break;
		case ACTION_FLIP_H:
			angleY = getBoundedAngle(180 + angleY);
			plottingTree.rotate(Tree.Y_AXIS, angleY);
			previousYangle = angleY;
			updatePlot();
			break;
		default:
			throw new IllegalArgumentException("Invalid action");
		}
		actionChoice = ACTION_NONE;
	}

	private void snapshot() {
		// apply input tree colors
		for (int i = 0; i < plottingTree.size(); i++) {
			final Path plottingPath = plottingTree.getPaths().get(i);
			final Path inputPath = snapshotTree.getPaths().get(i);
			plottingPath.setColor(inputPath.getColor());
			plottingPath.setNodeColors(inputPath.getNodeColors());
		}
		buildPlot();
		plot.setTitle("[X " + angleX + "deg Y " + angleY + "deg Z " + angleZ + "deg]");
		plot.setPreferredSize(frame.getWidth(), frame.getHeight());
		plot.showPlot();
		// make tree monochrome
		for (final Path p : plottingTree.getPaths()) {
			p.setColor(null);
			p.setNodeColors(null);
		}
	}

	/** IDE debug method **/
	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Map<String, Object> input = new HashMap<>();
		final Tree tree = new Tree("/home/tferr/Desktop/OP_1/OP_1.traces");
		input.put("tree", tree);
		ij.command().run(PlotterCmd.class, true, input);
	}

}
