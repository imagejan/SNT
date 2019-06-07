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
package sc.fiji.snt.annotation;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.scijava.util.ColorRGB;
import org.scijava.util.Colors;

import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.viewer.OBJMesh;

/**
 * Defines an Allen Reference Atlas (ARA) [Allen Mouse Common Coordinate
 * Framework] annotation. A Compartment is defined by either a UUID (as per
 * MouseLight's database) or its unique integer identifier. To improve
 * performance, a compartment's metadata (reference to its mesh, its aliases,
 * etc.) are not loaded at initialization, but retrieved only when such getters
 * are called.
 * 
 * @author Tiago Ferreira
 *
 */
public class AllenCompartment implements BrainAnnotation {

	private String name;
	private String acronym;
	private String[] aliases;
	private int structureId;
	private UUID uuid;
	private JSONObject jsonObj;

	/**
	 * Instantiates a new ARA annotation from an UUID (as used by MouseLight's
	 * database).
	 *
	 * @param uuid the ML UUID identifying the annotation
	 */
	public AllenCompartment(final UUID uuid) {
		this(null, uuid);
	}

	/**
	 * Instantiates a new ARA annotation from its identifier.
	 *
	 * @param id the integer identifying the annotation
	 */
	public AllenCompartment(final int id) {
		this(null, null);
		structureId = id;
	}

	protected AllenCompartment(final JSONObject jsonObj, final UUID uuid) {
		this.jsonObj = jsonObj;
		this.uuid = uuid;
	}

	private void loadJsonObj() {
		if (jsonObj != null) return;
		final JSONArray areaList = AllenUtils.getBrainAreasList();
		for (int n = 0; n < areaList.length(); n++) {
			final JSONObject area = (JSONObject) areaList.get(n);
			final UUID areaUUID = UUID.fromString(area.getString("id"));
			if (areaUUID.equals(uuid) || structureId ==  area.optInt("structureId")) {
				jsonObj = area;
				break;
				}
			}
		}

	private void initializeAsNeeded() {
		if (name != null) return;
		loadJsonObj();
		name = jsonObj.getString("name");
		acronym = jsonObj.getString("acronym");
		if (structureId == 0) structureId = jsonObj.optInt("structureId");
		if (uuid != null) uuid = UUID.fromString(jsonObj.getString("id"));
	}

	private String[] getArray(final JSONArray jArray) {
		final String[] array = new String[jArray.length()];
		for (int i = 0; i < jArray.length(); i++) {
			array[i] = (String) jArray.get(i);
		}
		return array;
	}

	protected int depth() {
		initializeAsNeeded();
		return jsonObj.getInt("depth");
	}

	protected int graphOrder() {
		return jsonObj.getInt("graphOrder");
	}

	protected String getStructureIdPath() {
		initializeAsNeeded();
		return jsonObj.optString("structureIdPath");
	}

	protected int getParentStructureId() {
		return jsonObj.optInt("parentStructureId");
	}

	/**
	 * Assesses if this annotation is parent of a specified compartment.
	 *
	 * @param childCompartment the compartment to be tested
	 * @return true, if successful, i.e., {@code childCompartment}'s
	 *         {@link #getTreePath()} contains this compartment
	 */
	public boolean contains(final AllenCompartment childCompartment) {
		return childCompartment.getStructureIdPath().contains(String.valueOf(id()));
	}

	/**
	 * Assesses if this annotation is a child of a specified compartment.
	 *
	 * @param parentCompartment the compartment to be tested
	 * @return true, if successful, i.e., {@link #getTreePath()} contains
	 *         {@code parentCompartment}
	 */
	public boolean containedBy(final AllenCompartment parentCompartment) {
		return getStructureIdPath().contains(String.valueOf(parentCompartment.id()));
	}

	/**
	 * Gets the tree path of this compartment. The TreePath is the list of parent
	 * compartments that uniquely identify this compartment in the ontologies
	 * hierarchical tree. The elements of the list are ordered with the root ('Whole
	 * Brain") as the first element of the list.
	 *
	 * @return the tree path that uniquely identifies this compartment as a node in
	 *         the CCF ontologies tree
	 */
	public List<AllenCompartment> getTreePath() {
		final String path = getStructureIdPath();
		final ArrayList<AllenCompartment> parentStructure = new ArrayList<>();
		for (final String structureID : path.split("/")) {
			if (structureID.isEmpty())
				continue;
			parentStructure.add(AllenUtils.getCompartment(Integer.parseInt(structureID)));
		}
		return parentStructure;
	}

	@Override
	public int id() {
		initializeAsNeeded();
		return structureId;
	}

	@Override
	public String name() {
		initializeAsNeeded();
		return name;
	}

	@Override
	public String acronym() {
		initializeAsNeeded();
		return acronym;
	}

	@Override
	public String[] aliases() {
		initializeAsNeeded();
		aliases = getArray(jsonObj.getJSONArray("aliases"));
		return aliases;
	}

	/**
	 * Checks whether a mesh is known to be available for this compartment.
	 *
	 * @return true, if a mesh is available.
	 */
	public boolean isMeshAvailable() {
		initializeAsNeeded();
		return jsonObj.getBoolean("geometryEnable");
	}

	@Override
	public OBJMesh getMesh() {
		if (id() == AllenUtils.BRAIN_ROOT_ID) return AllenUtils.getRootMesh(Colors.WHITE);
		final ColorRGB geometryColor = ColorRGB.fromHTMLColor("#" + jsonObj.optString("geometryColor", "ffffff"));
		OBJMesh mesh = null;
		try {
			final URL url = new URL("https://ml-neuronbrowser.janelia.org/static/allen/obj/" + jsonObj.getString("geometryFile"));
			mesh = new OBJMesh(url);
			mesh.setColor(geometryColor, 87.5f);
			mesh.setLabel(name);
		} catch (MalformedURLException | JSONException e) {
			SNTUtils.error("Could not retrieve mesh ", e);
		}
		return mesh;
	}

	@Override
	public String toString() {
		return name() + " [" + acronym + "]";
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this)
			return true;
		if (!(o instanceof AllenCompartment))
			return false;
		return uuid.equals(((AllenCompartment) o).uuid) 
				|| id() == ((AllenCompartment) o).id();
	}

}