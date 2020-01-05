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

package sc.fiji.snt.analysis.graph;


import org.jgrapht.graph.DefaultWeightedEdge;

import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.util.SWCPoint;

/**
 * An extremely simple implementation of a weighted edge for Tree<>Directed
 * Graph conversions.
 *
 * @author Tiago Ferreira
 */
public class SWCWeightedEdge extends DefaultWeightedEdge {

	private static final long serialVersionUID = 1L;

	public double getWeight() {
		return super.getWeight();
	}

	public double getLength() {
		return super.getWeight();
	}

	public SWCPoint getSource() {
		return (SWCPoint)super.getSource();
	}

	public SWCPoint getTarget() {
		return (SWCPoint)super.getTarget();
	}

	@Override
	public String toString() {
		return SNTUtils.formatDouble(getWeight(), 2);
	}

}