# @ImageJ ij


"""
file:       Sholl_Bulk_Analysis.groovy
author:     Tiago Ferreira
info:       Runs Sholl Analysis (Tracings) on a directory
"""

import sc.fiji.snt.plugin.ShollAnalysisBulkTreeCmd

ij.command().run(ShollAnalysisBulkTreeCmd.class, true)
