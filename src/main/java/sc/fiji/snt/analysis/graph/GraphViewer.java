package sc.fiji.snt.analysis.graph;

import net.imagej.ImageJ;
import net.imagej.lut.LUTService;

import org.scijava.Context;
import org.scijava.NullContextException;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.prefs.PrefService;

import sc.fiji.snt.SNTService;
import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.Tree;
import sc.fiji.snt.gui.GuiUtils;
import sc.fiji.snt.io.MouseLightLoader;

import javax.swing.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GraphViewer {
    @Parameter
    private Context context;
    private AnnotationGraph graph;

    public GraphViewer(final AnnotationGraph graph) {
        this.graph = graph;
    }
    public void setContext(final Context context) {
        if (context == null) throw new NullContextException("Context cannot be null!");
        context.inject(this);
    }
    private Context getContext() {
        if (context == null)
            setContext(new Context(CommandService.class, LUTService.class, PrefService.class));
        return context;
    }
    /**
     * Displays a graph in SNT's "Graph Viewer" featuring UI commands for
     * interactive visualization and export options.
     *
     * @return the assembled window
     */
    public Window show() {
        GuiUtils.setSystemLookAndFeel();
        final JDialog frame = new JDialog((JFrame) null, "SNT Graph Viewer");
        final AnnotationGraphAdapter graphAdapter = new AnnotationGraphAdapter(graph);
        final AnnotationGraphComponent graphComponent = new AnnotationGraphComponent(graphAdapter, getContext());
        frame.add(graphComponent.getJSplitPane());
        frame.pack();
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
        return frame;
    }

    public static void main(final String[] args) {
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        SNTUtils.setDebugMode(true);
        List<String> cellIds = new ArrayList<>();
        cellIds.add("AA0004");
        cellIds.add("AA0100");
        cellIds.add("AA0788");
        cellIds.add("AA1044");
        cellIds.add("AA0023");
        cellIds.add("AA0310");
        List<Tree> trees = new ArrayList<Tree>();
        for (String id : cellIds) {
            Tree tree = new MouseLightLoader(id).getTree("axon");
            trees.add(tree);
        }
        //List<Tree> trees = ij.context().getService(SNTService.class).demoTrees();
        final AnnotationGraph graph = new AnnotationGraph(trees, 40, 7);
        //graph.filterEdgesByWeight(20);
        // graph.removeOrphanedNodes();
        GraphViewer graphViewer = new GraphViewer(graph);
        graphViewer.setContext(ij.context());
        graphViewer.show();
    }
}