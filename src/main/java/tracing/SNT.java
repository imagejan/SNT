/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2017 Fiji developers.
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

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.swing.JButton;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.UIService;
import org.scijava.util.VersionUtils;

import fiji.util.Levenshtein;
import ij.IJ;
import ij.plugin.Colors;
import tracing.gui.GuiUtils;

/** Static utilities for SNT **/
public class SNT {

	private static Context context;
	private static LogService logService;
	public static final String VERSION = getVersion();

	private static boolean initialized;

	private SNT() {}

	private synchronized static void initialize() {
		if (initialized) return;
		if (context == null) context = (Context) IJ.runPlugIn("org.scijava.Context",
			"");
		if (logService == null) logService = context.getService(LogService.class);
		initialized = true;
	}

	private static String getVersion() {
		return VersionUtils.getVersion(tracing.SimpleNeuriteTracer.class);
	}

	@Deprecated
	public static void error(final String string) {
		initialize();
		uiService.showDialog(string, "Simple Neurite Tracer v" + VERSION,
			MessageType.ERROR_MESSAGE);
	}

	protected static void log(final String string) {
		initialize();
		logService.info("[SNT] " + string);
	}

	protected static void warn(final String string) {
		initialize();
		logService.warn("[SNT] " + string);
	}

	protected static void log(final String... strings) {
		if (strings != null) log(String.join(" ", strings));
	}

	protected static void debug(Object msg) {
		if (SimpleNeuriteTracer.verbose) {
			initialize();
			logService.debug("[SNT] " + msg);
		}
	}

	public static String stripExtension(final String filename) {
		final int lastDot = filename.lastIndexOf(".");
		if (lastDot > 0) return filename.substring(0, lastDot);
		return null;
	}

	//FIXME: Move to gui.GuiUtils
	// FIXME: Move to gui.GuiUtils
	@Deprecated
	protected static JButton smallButton(final String text) {
		return GuiUtils.smallButton(text);
	}

	protected static String getColorString(final Color color) {
		String name = "none";
		name = Colors.getColorName(color, name);
		if (!"none".equals(name))
			name = Colors.colorToString(color);
		return name;
	}

	protected static Color getColor(String colorName) {
		if (colorName == null)
			colorName = "none";
		Color color = null;
		color = Colors.getColor(colorName, color);
		if (color == null)
			color = Colors.decode(colorName, color);
		return color;
	}

	public static boolean fileAvailable(File file) {
		try {
			return file != null && file.exists();
		}
		catch (final SecurityException ignored) {
			return false;
		}
	}

	public static File findClosestPair(final File file, final String pairExt) {
		try {
			SNT.debug("Finding closest pair for " + file);
			final File dir = file.getParentFile();
			final String[] list = dir.list(new FilenameFilter() {
				@Override
				public boolean accept(final File f, final String s) {
					return s.endsWith(pairExt);
				}
			});
			SNT.debug("Found " + list.length + " " + pairExt + " files");
			if (list.length == 0) return null;
			Arrays.sort(list);
			String dirPath = dir.getAbsolutePath();
			if (!dirPath.endsWith(File.separator)) dirPath += File.separator;
			int cost = Integer.MAX_VALUE;
			final String seed = stripExtension(file.getName().toLowerCase());
			String closest = null;
			final Levenshtein levenshtein = new Levenshtein(5, 10, 1, 5, 5, 0);
			for (final String item : list) {
				final String filename = stripExtension(Paths.get(item).getFileName()
					.toString()).toLowerCase();
				final int currentCost = levenshtein.cost(seed, filename);
				SNT.debug("Levenshtein cost for '" + item + "': " + currentCost);
				if (currentCost <= cost) {
					cost = currentCost;
					closest = item;
				}
			}
			SNT.debug("Identified pair '" + closest + "'");
			return new File(dirPath + closest);
		}
		catch (final SecurityException | NullPointerException ignored) {
			return null;
		}
	}
}
