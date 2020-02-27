# @Context context
# @LegacyService ls
# @DatasetService ds
# @DisplayService display
# @LogService log
# @SNTService snt
# @StatusService status
# @UIService ui
# @PlotService plotService

"""
file:       Analysis_Demo.py
author:     Tiago Ferreira, Cameron Arshadi
version:    20190610
info:       A Jython demo of how SNT can analyze neuronal reconstructions fetched
            from online databases such as MouseLight, NeuroMorpho or FlyCircuit.
            This demo requires internet connection and assumes you've already ran
            Analysis_Demo_(Interactive).py 
"""

import math
from collections import defaultdict

from sc.fiji.snt import (Path, PathAndFillManager, SNT, SNTUI, Tree)
from sc.fiji.snt.analysis import (RoiConverter, MultiTreeColorMapper, TreeAnalyzer,
        TreeColorMapper, TreeStatistics)

from sc.fiji.snt.io import (MouseLightLoader, NeuroMorphoLoader)
from sc.fiji.snt.viewer import (Viewer2D, Viewer3D)


def run():

    # To load a neuron from a local file, one could use:
    tree = Tree.fromFile("path/to/local/file")
    if not tree:
       print("path/to/local/file is not a valid file")
    else:
        # Do something with it, e.g.
        axon = tree.subTree("axon")

    # (See Analysis_Demo_(Interactive).py for further details on a SNT Tree)
    # For now, we'll import one of the largest cells in the MouseLight database
    loader = MouseLightLoader("AA0100")
    if not loader.isDatabaseAvailable():
        ui.showDialog("Could not connect to ML database", "Error")
        return
    if not loader.idExists():
        ui.showDialog("Somehow the specified id was not found", "Error")
        return

    # All of the raw data in the MouseLight database is stored as JSONObjects.
    # (https://stleary.github.io/JSON-java/index.html). If needed, these could
    # be access as follows:
    #all_data = loader.getJSON()

    # To extract a compartment:
    d_tree = loader.getTree('dendrites', None)  # compartment, color
    a_tree = loader.getTree('axon', None)
    for tree in [d_tree, a_tree]:

        print("Parsing %s" % tree.getLabel())
        d_stats = TreeStatistics(tree)

        # NB: SummaryStatistics should be more performant than DescriptiveStatistics
        # https://morphonets.github.io/SNT/index.html?sc/fiji/snt/analysis/TreeStatistics.html
        metric = TreeStatistics.INTER_NODE_DISTANCE  # same as "inter-node distance"
        summary_stats = d_stats.getSummaryStats(metric)
        d_stats.getHistogram(metric).show()
        print("The average inter-node distance is %d" % summary_stats.getMean())

        dsummary = d_stats.getDescriptiveStats(TreeStatistics.INTER_NODE_DISTANCE)
        print("The average inter-node distance is %d" % dsummary.getMean())

        # We can get the volume of a compartment by approximating the volume of
        # each path, and summing to total. For info on the assumptions made in
        # this volume calculation, have a look at Tree's documentation:
        # https://morphonets.github.io/SNT/index.html?sc/fiji/snt/Tree.html
        compartment_volume = tree.getApproximatedVolume()
        print("Approximate volume of tracing is %d cubic microns" % compartment_volume)

        # Let's find the minimum bounding box containing all existing nodes:
        # https://morphonets.github.io/SNT/index.html?sc/fiji/snt/util/BoundingBox.html
        bb = tree.getBoundingBox(True)
        bb_dim = bb.getDimensions(False)
        print("Dimensions of bounding box containing all nodes (in micrometers): {} x {} x {}".format(*bb_dim))

    # To load a neuron from NeuroMorpho.org, use NeuroMorphoLoader.
    # https://morphonets.github.io/SNT/index.html?sc/fiji/snt/io/NeuroMorphoLoader.html
    # We choose a reconstruction with traced node radii:
    # http://neuromorpho.org/neuron_info.jsp?neuron_name=Adol-20100419cell1
    nm_loader = NeuroMorphoLoader()
    if nm_loader.isDatabaseAvailable():
        nm_tree = nm_loader.getTree("Adol-20100419cell1")

        # Like before, we can plot data using the convenience of TreeStatistics
        if nm_tree is not None:
            nm_stats = TreeStatistics(nm_tree)
            nm_metric = TreeStatistics.MEAN_RADIUS
            nm_stats.getHistogram("mean radius").show()

            # To build custom plots, we can use SciJava's PlotService. E.g., we
            # can see how node radius changes as a function of path distance
            # from the root node.
            radius_dict = defaultdict(list)
            nm_graph = nm_tree.getGraph()
            nm_root = nm_graph.getRoot()
            l = []
            for v in nm_graph.vertexSet():
                r = v.radius
                path_length = nm_graph.getShortestPath(nm_root, v).getWeight()
                l.append((path_length, r))

            l = sorted(l, key=lambda x: x[0])
            xs, ys = zip(*l)

            plot = plotService.newXYPlot()
            plot.setTitle("Mean Path Radius vs Distance to Root")
            series = plot.addXYSeries()
            series.setLabel("Adol-20100419cell1")
            series.setValues(xs, ys)
            ui.show(plot)


run()
