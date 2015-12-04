package edu.uci.ics.cs.graspan.dispatcher;

import java.io.IOException;
import java.util.logging.Logger;

import edu.uci.ics.cs.graspan.computation.Engine;
import edu.uci.ics.cs.graspan.datastructures.GlobalParameters;
import edu.uci.ics.cs.graspan.scheduler.BasicScheduler;
import edu.uci.ics.cs.graspan.support.GDTCLogger;

public class DTCComputer {

	private static final Logger logger = GDTCLogger.getLogger("graphdtc dtccomputer");

	public static void main(String args[]) throws IOException {

		GlobalParameters.setBasefilename(args[0]);
		GlobalParameters.setNumParts(Integer.parseInt(args[1]));
		GlobalParameters.setNumPartsPerComputation(Integer.parseInt(args[2]));
		GlobalParameters.setReloadPlan(args[3]);
		GlobalParameters.setPreservePlan(args[4]);

		BasicScheduler basicScheduler = new BasicScheduler();
		basicScheduler.initScheduler();
		logger.info("Initialized scheduler.");

		Engine engine = new Engine(basicScheduler.getPartstoLoad());
		engine.run();
	}
}