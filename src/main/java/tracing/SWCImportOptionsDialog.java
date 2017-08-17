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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import ij.IJ;
import ij.Prefs;
import ij.gui.GUI;

@SuppressWarnings("serial")
public class SWCImportOptionsDialog extends Dialog implements WindowListener, ActionListener, ItemListener {

	boolean succeeded = false;

	Checkbox replaceExistingPathsCheckbox = new Checkbox("Replace existing paths?");

	Checkbox ignoreCalibrationCheckbox = new Checkbox("Ignore calibration; assume SWC uses image co-ordinates");

	Checkbox applyOffsetCheckbox = new Checkbox("Apply offset to SWC file co-ordinates");
	Checkbox applyScaleCheckbox = new Checkbox("Apply scale to SWC file co-ordinates");

	String offsetDefault = "0.0";
	String scaleDefault = "1.0";

	TextField xOffsetTextField = new TextField(offsetDefault);
	TextField yOffsetTextField = new TextField(offsetDefault);
	TextField zOffsetTextField = new TextField(offsetDefault);

	TextField xScaleTextField = new TextField(scaleDefault);
	TextField yScaleTextField = new TextField(scaleDefault);
	TextField zScaleTextField = new TextField(scaleDefault);

	Button okButton = new Button("Load");
	Button cancelButton = new Button("Cancel");
	Button restoreToDefaultsButton = new Button("Restore default options");

	protected void setFieldsFromPrefs() {

		xOffsetTextField.setText(Prefs.get("tracing.SWCImportOptionsDialog.xOffset", offsetDefault));
		yOffsetTextField.setText(Prefs.get("tracing.SWCImportOptionsDialog.yOffset", offsetDefault));
		zOffsetTextField.setText(Prefs.get("tracing.SWCImportOptionsDialog.zOffset", offsetDefault));

		xScaleTextField.setText(Prefs.get("tracing.SWCImportOptionsDialog.xScale", scaleDefault));
		yScaleTextField.setText(Prefs.get("tracing.SWCImportOptionsDialog.yScale", scaleDefault));
		zScaleTextField.setText(Prefs.get("tracing.SWCImportOptionsDialog.zScale", scaleDefault));

		if (Prefs.get("tracing.SWCImportOptionsDialog.applyOffset", "false").equals("true"))
			applyOffsetCheckbox.setState(true);
		else {
			applyOffsetCheckbox.setState(false);
			xOffsetTextField.setText(offsetDefault);
			yOffsetTextField.setText(offsetDefault);
			zOffsetTextField.setText(offsetDefault);
		}

		if (Prefs.get("tracing.SWCImportOptionsDialog.applyScale", "false").equals("true"))
			applyScaleCheckbox.setState(true);
		else {
			applyScaleCheckbox.setState(false);
			xScaleTextField.setText(scaleDefault);
			yScaleTextField.setText(scaleDefault);
			zScaleTextField.setText(scaleDefault);
		}

		ignoreCalibrationCheckbox
				.setState(Prefs.get("tracing.SWCImportOptionsDialog.ignoreCalibration", "false").equals("true"));

		replaceExistingPathsCheckbox
				.setState(Prefs.get("tracing.SWCImportOptionsDialog.replaceExistingPaths", "true").equals("true"));

		updateEnabled();
	}

	@Override
	public void actionPerformed(final ActionEvent e) {

		final Object source = e.getSource();
		if (source == okButton) {
			try {
				Double.parseDouble(xOffsetTextField.getText());
				Double.parseDouble(yOffsetTextField.getText());
				Double.parseDouble(zOffsetTextField.getText());
				Double.parseDouble(xScaleTextField.getText());
				Double.parseDouble(yScaleTextField.getText());
				Double.parseDouble(zScaleTextField.getText());
			} catch (final NumberFormatException nfe) {
				IJ.error("Couldn't parse an offset or scale as a number: " + nfe);
				return;
			}
			succeeded = true;
			saveFieldsToPrefs();
			dispose();
		} else if (source == cancelButton) {
			dispose();
		} else if (source == restoreToDefaultsButton) {
			restoreToDefaults();
		}
	}

	@Override
	public void itemStateChanged(final ItemEvent e) {

		final Object source = e.getSource();

		if (source == applyScaleCheckbox || source == applyOffsetCheckbox)
			updateEnabled();
	}

	protected void enableTextField(final TextField tf, final boolean enabled, final String defaultValue) {
		tf.setEnabled(enabled);
		tf.setVisible(enabled);
		if (!enabled)
			tf.setText(defaultValue);
	}

	protected void updateEnabled() {
		final boolean manualScale = applyScaleCheckbox.getState();
		enableTextField(xScaleTextField, manualScale, scaleDefault);
		enableTextField(yScaleTextField, manualScale, scaleDefault);
		enableTextField(zScaleTextField, manualScale, scaleDefault);
		final boolean manualOffset = applyOffsetCheckbox.getState();
		enableTextField(xOffsetTextField, manualOffset, offsetDefault);
		enableTextField(yOffsetTextField, manualOffset, offsetDefault);
		enableTextField(zOffsetTextField, manualOffset, offsetDefault);
		pack();
	}

	protected void saveFieldsToPrefs() {

		Prefs.set("tracing.SWCImportOptionsDialog.xOffset", xOffsetTextField.getText());
		Prefs.set("tracing.SWCImportOptionsDialog.yOffset", yOffsetTextField.getText());
		Prefs.set("tracing.SWCImportOptionsDialog.zOffset", zOffsetTextField.getText());
		Prefs.set("tracing.SWCImportOptionsDialog.xScale", xScaleTextField.getText());
		Prefs.set("tracing.SWCImportOptionsDialog.yScale", yScaleTextField.getText());
		Prefs.set("tracing.SWCImportOptionsDialog.zScale", zScaleTextField.getText());

		Prefs.set("tracing.SWCImportOptionsDialog.applyOffset", applyOffsetCheckbox.getState());

		Prefs.set("tracing.SWCImportOptionsDialog.applyScale", applyScaleCheckbox.getState());

		Prefs.set("tracing.SWCImportOptionsDialog.ignoreCalibration", ignoreCalibrationCheckbox.getState());

		Prefs.set("tracing.SWCImportOptionsDialog.replaceExistingPaths", replaceExistingPathsCheckbox.getState());

		Prefs.savePreferences();
	}

	public void restoreToDefaults() {
		ignoreCalibrationCheckbox.setState(false);
		applyScaleCheckbox.setState(false);
		applyOffsetCheckbox.setState(false);
		updateEnabled();
		saveFieldsToPrefs();
	}

	public SWCImportOptionsDialog(final String title) {

		super(IJ.getInstance(), title, true);

		addWindowListener(this);

		okButton.addActionListener(this);
		cancelButton.addActionListener(this);
		restoreToDefaultsButton.addActionListener(this);

		applyOffsetCheckbox.addItemListener(this);
		applyScaleCheckbox.addItemListener(this);

		final Panel offsetPanel = new Panel();
		offsetPanel.setLayout(new BorderLayout());
		offsetPanel.add(xOffsetTextField, BorderLayout.NORTH);
		offsetPanel.add(yOffsetTextField, BorderLayout.CENTER);
		offsetPanel.add(zOffsetTextField, BorderLayout.SOUTH);

		final Panel scalePanel = new Panel();
		scalePanel.setLayout(new BorderLayout());
		scalePanel.add(xScaleTextField, BorderLayout.NORTH);
		scalePanel.add(yScaleTextField, BorderLayout.CENTER);
		scalePanel.add(zScaleTextField, BorderLayout.SOUTH);

		final Panel okCancelPanel = new Panel();
		okCancelPanel.setLayout(new FlowLayout());
		okCancelPanel.add(okButton);
		okCancelPanel.add(cancelButton);

		final Panel buttonsPanel = new Panel();
		buttonsPanel.setLayout(new BorderLayout());
		buttonsPanel.add(okCancelPanel, BorderLayout.WEST);
		buttonsPanel.add(restoreToDefaultsButton, BorderLayout.EAST);

		setLayout(new GridBagLayout());
		final GridBagConstraints c = new GridBagConstraints();

		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.LINE_START;
		add(replaceExistingPathsCheckbox, c);

		c.gridy++;
		add(ignoreCalibrationCheckbox, c);

		c.gridy++;
		add(applyOffsetCheckbox, c);

		c.gridy++;
		c.anchor = GridBagConstraints.CENTER;
		add(offsetPanel, c);

		c.gridy++;
		c.anchor = GridBagConstraints.LINE_START;
		add(applyScaleCheckbox, c);

		c.gridy++;
		c.anchor = GridBagConstraints.CENTER;
		add(scalePanel, c);

		c.gridy++;
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.BOTH;
		add(buttonsPanel, c);

		setFieldsFromPrefs();

		pack();
		GUI.center(this);
		setVisible(true);
	}

	@Override
	public void windowClosing(final WindowEvent e) {
		dispose();
	}

	@Override
	public void windowActivated(final WindowEvent e) {
	}

	@Override
	public void windowDeactivated(final WindowEvent e) {
	}

	@Override
	public void windowClosed(final WindowEvent e) {
	}

	@Override
	public void windowOpened(final WindowEvent e) {
	}

	@Override
	public void windowIconified(final WindowEvent e) {
	}

	@Override
	public void windowDeiconified(final WindowEvent e) {
	}

	public boolean getIgnoreCalibration() {
		return ignoreCalibrationCheckbox.getState();
	}

	public boolean getReplaceExistingPaths() {
		return replaceExistingPathsCheckbox.getState();
	}

	public double getXOffset() {
		return Double.parseDouble(xOffsetTextField.getText());
	}

	public double getYOffset() {
		return Double.parseDouble(yOffsetTextField.getText());
	}

	public double getZOffset() {
		return Double.parseDouble(zOffsetTextField.getText());
	}

	public double getXScale() {
		return Double.parseDouble(xScaleTextField.getText());
	}

	public double getYScale() {
		return Double.parseDouble(yScaleTextField.getText());
	}

	public double getZScale() {
		return Double.parseDouble(zScaleTextField.getText());
	}

}
