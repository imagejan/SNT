/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2019 Fiji developers.
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

package sc.fiji.snt.analysis;

import java.awt.Color;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.imagej.ImageJ;
import net.imagej.lut.LUTService;
import net.imglib2.display.ColorTable;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.scijava.Context;
import org.scijava.plugin.Parameter;

import sholl.ProfileEntry;
import sholl.UPoint;
import sholl.math.LinearProfileStats;
import sc.fiji.snt.Path;
import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.Tree;
import sc.fiji.snt.analysis.sholl.TreeParser;
import sc.fiji.snt.util.PointInImage;
import sc.fiji.snt.viewer.MultiViewer2D;
import sc.fiji.snt.viewer.Viewer2D;

/**
 * Class for color coding {@link Tree}s.
 *
 * @author Tiago Ferreira
 */
public class TreeColorMapper extends ColorMapper {

	/* For convenience keep references to TreeAnalyzer fields */

	/** Flag for {@value #BRANCH_ORDER} mapping. */
	public static final String BRANCH_ORDER = TreeAnalyzer.BRANCH_ORDER;
	/** Flag for {@value #LENGTH} mapping. */
	public static final String LENGTH = TreeAnalyzer.LENGTH;
	/** Flag for {@value #N_BRANCH_POINTS} mapping. */
	public static final String N_BRANCH_POINTS = TreeAnalyzer.N_BRANCH_POINTS;
	/** Flag for {@value #N_NODES} mapping. */
	public static final String N_NODES = TreeAnalyzer.N_NODES;
	/** Flag for {@value #MEAN_RADIUS} mapping. */
	public static final String MEAN_RADIUS = TreeAnalyzer.MEAN_RADIUS;
	/** Flag for {@value #NODE_RADIUS} mapping. */
	public static final String NODE_RADIUS = TreeAnalyzer.NODE_RADIUS;
	/** Flag for {@value #X_COORDINATES} mapping. */
	public static final String X_COORDINATES = TreeAnalyzer.X_COORDINATES;
	/** Flag for {@value #Y_COORDINATES} mapping. */
	public static final String Y_COORDINATES = TreeAnalyzer.Y_COORDINATES;
	/** Flag for {@value #Z_COORDINATES} mapping. */
	public static final String Z_COORDINATES = TreeAnalyzer.Z_COORDINATES;
	/** See {@link TreeAnalyzer#VALUES}. */
	public static final String VALUES = TreeAnalyzer.VALUES;
	/** Flag for {@value #PATH_DISTANCE} mapping. */
	public static final String PATH_DISTANCE = "Path distance to soma";
	/** Flag for {@value #TAG_FILENAME} mapping. */
	public static final String TAG_FILENAME = "Tags/filename";
	private static final String INTERNAL_COUNTER = "Id";


	@Parameter
	private LUTService lutService;

	protected ArrayList<Path> paths;
	private Map<String, URL> luts;
	private int internalCounter = 1;
	private final List<Tree> mappedTrees;

	/**
	 * Instantiates the Colorizer.
	 *
	 * @param context the SciJava application context providing the services
	 *          required by the class
	 */
	public TreeColorMapper(final Context context) {
		this();
		context.inject(this);
	}

	/**
	 * Instantiates the Colorizer. Note that because the instance is not aware of
	 * any context, script-friendly methods that use string as arguments may fail
	 * to retrieve referenced Scijava objects.
	 */
	public TreeColorMapper() {
		mappedTrees = new ArrayList<>();
	}

	private void initLuts() {
		if (luts == null) luts = lutService.findLUTs();
	}

	public ColorTable getColorTable(final String lut) {
		initLuts();
		for (final Map.Entry<String, URL> entry : luts.entrySet()) {
			if (entry.getKey().contains(lut)) {
				try {
					return lutService.loadLUT(entry.getValue());
				}
				catch (final IOException e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}

	protected void mapToProperty(final String measurement,
		final ColorTable colorTable)
	{
		map(measurement, colorTable);
		final String cMeasurement = normalizedMeasurement(measurement);
		switch (cMeasurement) {
			case PATH_DISTANCE:
				final TreeParser parser = new TreeParser(new Tree(paths));
				try {
					parser.setCenter(TreeParser.PRIMARY_NODES_SOMA);
				}
				catch (final IllegalArgumentException ignored) {
					SNTUtils.log(
						"No soma attribute found... Defaulting to average of all root nodes");
					parser.setCenter(TreeParser.PRIMARY_NODES_ANY);
				}
				final UPoint center = parser.getCenter();
				final PointInImage root = new PointInImage(center.x, center.y,
					center.z);
				mapPathDistances(root);
				break;
			case BRANCH_ORDER:
			case LENGTH:
			case MEAN_RADIUS:
			case N_NODES:
			case N_BRANCH_POINTS:
			case INTERNAL_COUNTER:
			case TAG_FILENAME:
				mapToPathProperty(cMeasurement, colorTable);
				break;
			case X_COORDINATES:
			case Y_COORDINATES:
			case Z_COORDINATES:
			case NODE_RADIUS:
			case VALUES:
				mapToNodeProperty(cMeasurement, colorTable);
				break;
			default:
				throw new IllegalArgumentException("Unknown parameter");
		}
	}

	private void mapToPathProperty(final String measurement,
		final ColorTable colorTable)
	{
		final List<MappedPath> mappedPaths = new ArrayList<>();
		switch (measurement) {
			case BRANCH_ORDER:
				integerScale = true;
				for (final Path p : paths)
					mappedPaths.add(new MappedPath(p, (double) p.getOrder()));
				break;
			case LENGTH:
				integerScale = false;
				for (final Path p : paths)
					mappedPaths.add(new MappedPath(p, p.getLength()));
				break;
			case MEAN_RADIUS:
				integerScale = false;
				for (final Path p : paths)
					mappedPaths.add(new MappedPath(p, p.getMeanRadius()));
				break;
			case N_NODES:
				integerScale = true;
				for (final Path p : paths)
					mappedPaths.add(new MappedPath(p, (double) p.size()));
				break;
			case N_BRANCH_POINTS:
				integerScale = true;
				for (final Path p : paths)
					mappedPaths.add(new MappedPath(p, (double) p.findJunctions()
						.size()));
				break;
			case INTERNAL_COUNTER:
				integerScale = true;
				for (final Path p : paths)
					mappedPaths.add(new MappedPath(p, (double) internalCounter));
				break;
			case TAG_FILENAME:
				integerScale = true;
				final List<MappedTaggedPath> mappedTaggedPaths = new ArrayList<>();
				final TreeSet<String> tags = new TreeSet<>();
				for (final Path p : paths) {
					final MappedTaggedPath mp = new MappedTaggedPath(p);
					mappedTaggedPaths.add(mp);
					tags.add(mp.mappedTag);
				}
				final int nTags = tags.size();
				for (final MappedTaggedPath p : mappedTaggedPaths) {
					mappedPaths.add(new MappedPath(p.path, (double) tags.headSet(
						p.mappedTag).size() / nTags));
				}
				break;
			default:
				throw new IllegalArgumentException("Unknown parameter");
		}
		for (final MappedPath mp : mappedPaths) {
			mp.path.setColor(getColor(mp.mappedValue));
		}
	}

	private void mapToNodeProperty(final String measurement,
		final ColorTable colorTable)
	{
		if (Double.isNaN(min) || Double.isNaN(max) || min > max) {
			final TreeStatistics tStats = new TreeStatistics(new Tree(paths));
			final SummaryStatistics sStats = tStats.getSummaryStats(measurement);
			setMinMax(sStats.getMin(), sStats.getMax());
		}
		for (final Path p : paths) {
			final Color[] colors = new Color[p.size()];
			for (int node = 0; node < p.size(); node++) {
				double value;
				switch (measurement) {
					case X_COORDINATES:
						value = p.getNode(node).x;
						break;
					case Y_COORDINATES:
						value = p.getNode(node).y;
						break;
					case Z_COORDINATES:
						value = p.getNode(node).z;
						break;
					case NODE_RADIUS:
						value = p.getNodeRadius(node);
						break;
					case VALUES:
						value = p.getNodeValue(node);
						break;
					default:
						throw new IllegalArgumentException("Unknow parameter");
				}
				colors[node] = getColor(value);
			}
			p.setNodeColors(colors);
		}
	}

	private void mapPathDistances(final PointInImage root) {
		if (root == null) {
			throw new IllegalArgumentException("source point cannot be null");
		}

		final boolean setLimits = (Double.isNaN(min) || Double.isNaN(max) ||
			min > max);
		if (setLimits) {
			min = Float.MAX_VALUE;
			max = 0f;
		}
		SNTUtils.log("Node values will be wiped after distance calculations");

		// 1st pass: Calculate distances for primary paths.
		for (final Path p : paths) {
			if (p.isPrimary()) {
				double dx = p.getNode(0).distanceTo(root);
				p.setNodeValue(dx, 0);
				for (int i = 1; i < p.size(); ++i) {
					final double dxPrev = p.getNodeValue(i - 1);
					final PointInImage prev = p.getNode(i - 1);
					final PointInImage curr = p.getNode(i);
					dx = curr.distanceTo(prev) + dxPrev;
					p.setNodeValue(dx, i);
					if (setLimits) {
						if (dx > max) max = dx;
						if (dx < min) min = dx;
					}
				}
			}
		}

		// 2nd pass: Calculate distances for remaining paths
		for (final Path p : paths) {
			if (p.isPrimary()) continue;
			final PointInImage pim = p.getNode(0);
			double dx = p.getStartJoins().getNodeValue(p.getStartJoins().getNodeIndex(pim)); // very inefficient
			p.setNodeValue(dx, 0);
			for (int i = 1; i < p.size(); ++i) {
				final double dxPrev = p.getNodeValue(i - 1);
				final PointInImage prev = p.getNode(i - 1);
				final PointInImage curr = p.getNode(i);
				dx = curr.distanceTo(prev) + dxPrev;
				p.setNodeValue(dx, i);
				if (setLimits) {
					if (dx > max) max = dx;
					if (dx < min) min = dx;
				}
			}
		}

		// now color nodes
		if (setLimits) setMinMax(min, max);
		SNTUtils.log("Coloring nodes by path distance to " + root);
		SNTUtils.log("Range of mapped distances: " + min + "-" + max);
		for (final Path p : paths) {
			for (int node = 0; node < p.size(); node++) {
				// if (p.isPrimary()) System.out.println(p.getNodeValue(node));
				p.setNodeColor(getColor(p.getNodeValue(node)), node);
			}
		}

		// Wipe node values so that computed distances don't
		// get mistakenly interpreted as pixel intensities
		paths.stream().forEach(p -> p.setNodeValues(null));

	}

	/**
	 * Colorizes a tree using Sholl data.
	 *
	 * @param tree the tree to be colorized
	 * @param stats the LinearProfileStats instance containing the mapping
	 *          profile. if a polynomial fit has been successfully performed,
	 *          mapping is done against the fitted data, otherwise sampled
	 *          intersections are used.
	 * @param colorTable the color table specifying the color mapping. Null not
	 *          allowed.
	 */
	public void map(final Tree tree, final LinearProfileStats stats,
		final ColorTable colorTable)
	{
		final UPoint ucenter = stats.getProfile().center();
		if (ucenter == null) {
			throw new IllegalArgumentException("Center unknown");
		}
		paths = tree.list();
		this.colorTable = colorTable;
		final boolean useFitted = stats.validFit();
		SNTUtils.log("Mapping to fitted values: " + useFitted);
		setMinMax(stats.getMin(useFitted), stats.getMax(useFitted));
		final PointInImage center = new PointInImage(ucenter.x, ucenter.y,
			ucenter.z);
		final double stepSize = stats.getProfile().stepSize();
		final double stepSizeSq = stepSize * stepSize;
		for (final ProfileEntry entry : stats.getProfile().entries()) {
			final double entryRadiusSqed = entry.radiusSquared();
			for (final Path p : paths) {
				for (int node = 0; node < p.size(); node++) {
					final double dx = center.distanceSquaredTo(p.getNode(node));
					if (dx >= entryRadiusSqed && dx < entryRadiusSqed + stepSizeSq) {
						p.setNodeColor(getColor(entry.count), node);
					}
				}
			}
		}
		mappedTrees.add(tree);
	}

	/**
	 * Colorizes a tree after the specified measurement.
	 *
	 * @param tree the tree to be colorized
	 * @param measurement the measurement ({@link #BRANCH_ORDER} }{@link #LENGTH},
	 *          etc.)
	 * @param colorTable the color table specifying the color mapping. Null not
	 *          allowed.
	 */
	public void map(final Tree tree, final String measurement,
		final ColorTable colorTable)
	{
		try {
			this.paths = tree.list();
			mapToProperty(measurement, colorTable);
			mappedTrees.add(tree);
		} catch (final IllegalArgumentException ignored) {
			final String educatedGuess = tryReallyHardToGuessMetric(measurement);
			System.out.println("Mapping to \""+ measurement +"\" failed. Assuming \""+ educatedGuess+"\"");
			if ("unknown".equals(educatedGuess))
				throw new IllegalArgumentException("Unknown parameter: "+ measurement);
			else {
				mapToProperty(educatedGuess, colorTable);
				mappedTrees.add(tree);
			}
		}
	}

	private String tryReallyHardToGuessMetric(final String guess) {
		final String normGuess = guess.toLowerCase();
		if (normGuess.indexOf("length") != -1) {
			return LENGTH;
		}
		if (normGuess.indexOf("branch points") != -1 || normGuess.indexOf("bps") != -1) {
			return N_BRANCH_POINTS;
		}
		if (normGuess.indexOf("strahler") != -1 || normGuess.indexOf("horton") != -1 || normGuess.indexOf("order") != -1) {
			return BRANCH_ORDER;
		}
		return guess;
	}

	/**
	 * Colorizes a tree after the specified measurement. Mapping bounds are
	 * automatically determined.
	 *
	 * @param tree the tree to be plotted
	 * @param measurement the measurement ({@link #BRANCH_ORDER} }{@link #LENGTH},
	 *          etc.)
	 * @param lut the lookup table specifying the color mapping
	 */
	public void map(final Tree tree, final String measurement, final String lut) {
		map(tree, measurement, getColorTable(lut));
	}

	/**
	 * Colorizes a list of trees, with each tree being assigned a LUT index.
	 *
	 * @param trees the list of trees to be colorized
	 * @param lut the lookup table specifying the color mapping
	 */
	public void mapTrees(final List<Tree> trees, final String lut) {
		setMinMax(1, trees.size());
		for (final ListIterator<Tree> it = trees.listIterator(); it.hasNext();) {
			map(it.next(), INTERNAL_COUNTER, lut);
			internalCounter = it.nextIndex();
		}
		mappedTrees.addAll(trees);
	}

	/**
	 * Gets the available LUTs.
	 *
	 * @return the set of keys, corresponding to the set of LUTs available
	 */
	public Set<String> getAvalailableLuts() {
		initLuts();
		return luts.keySet();
	}

	public MultiViewer2D getMultiViewer() {
		final List<Viewer2D> viewers = new ArrayList<>(mappedTrees.size());
		mappedTrees.forEach(tree -> {
			final Viewer2D viewer = new Viewer2D(new Context());
			viewer.addTree(tree);
			viewers.add(viewer);
		});
		final MultiViewer2D multiViewer = new MultiViewer2D(viewers);
		if (colorTable != null && !mappedTrees.isEmpty())
			multiViewer.setColorBarLegend(colorTable, min, max);
		return multiViewer;
	}

	private class MappedPath {

		private final Path path;
		private final Double mappedValue;

		private MappedPath(final Path path, final Double mappedValue) {
			this.path = path;
			this.mappedValue = mappedValue;
			if (mappedValue > max) max = mappedValue;
			if (mappedValue < min) min = mappedValue;
		}
	}

	private class MappedTaggedPath {

		private final Pattern pattern = Pattern.compile("\\{(\\w+)\\b");
		private final Path path;
		private final String mappedTag;

		private MappedTaggedPath(final Path path) {
			this.path = path;
			final Matcher matcher = pattern.matcher(path.getName());
			mappedTag = (matcher.find()) ? matcher.group(1) : "";
		}
	}

	/* IDE debug method */
	public static void main(final String... args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final List<Tree> trees = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			final Tree tree = new Tree(SNTUtils.randomPaths());
			tree.rotate(Tree.Z_AXIS, i * 20);
			trees.add(tree);
		}
		final Viewer2D plot = new Viewer2D(ij.context());
		plot.addTrees(trees, "Ice.lut");
		plot.addColorBarLegend();
		plot.showPlot();
	}

}
