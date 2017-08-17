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

package tracing.gui;

import java.io.File;

import org.scijava.Context;
import org.scijava.plugin.Parameter;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.DialogPrompt.Result;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;

import tracing.SNT;

public class GuiUtils {

	@Parameter
	private UIService uiService;

	private final String VERSION;

	public GuiUtils(final Context context) {
		context.inject(this);
		VERSION = "SNT v" + SNT.VERSION;
	}

	public void error(final String message, final String title) {
		uiService.showDialog(message, (title == null) ? VERSION + " Error" : title,
			MessageType.ERROR_MESSAGE);
	}

	public void infoMsg(final String message, final String title) {
		uiService.showDialog(message, (title == null) ? VERSION : title,
			MessageType.INFORMATION_MESSAGE);
	}

	public Result yesNoPrompt(final String message, final String title) {
		return uiService.showDialog(message, (title == null) ? VERSION : title,
			MessageType.QUESTION_MESSAGE, OptionType.YES_NO_OPTION);
	}

	public Result yesNoCancelPrompt(final String message, final String title) {
		return uiService.showDialog(message, (title == null) ? VERSION : title,
			MessageType.QUESTION_MESSAGE, OptionType.YES_NO_CANCEL_OPTION);
	}

	public File openFile(final String title, final File initialChoice) {
		return uiService.chooseFile(title, null, FileWidget.OPEN_STYLE);
	}

}
