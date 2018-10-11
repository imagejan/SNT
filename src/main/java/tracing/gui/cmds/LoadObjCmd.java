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

package tracing.gui.cmds;

import java.awt.Color;
import java.io.File;

import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.util.ColorRGB;

import net.imagej.ImageJ;
import tracing.SNTService;
import tracing.gui.GuiUtils;

/**
 * Command for loading an OBJ in Reconstruction Viewer
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, label = "Load OBJ...")
public class LoadObjCmd extends ContextCommand {

	@Parameter
	private SNTService sntService;

	@Parameter(label = "OBJ file", style="extensions:obj")
	private File file;

	@Parameter(label = "Color", description = "Color for rendering imported file")
	private ColorRGB color;


	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		try {
			sntService.getReconstructionViewer().loadOBJ(file.getAbsolutePath(), new Color(color.getARGB()));
		} catch (final UnsupportedOperationException | NullPointerException exc) {
			cancel("SNT's Legacy Viewer is not open");
		}
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(LoadObjCmd.class, true);
	}

}