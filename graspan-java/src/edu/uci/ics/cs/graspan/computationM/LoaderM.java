package edu.uci.ics.cs.graspan.computationM;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Logger;

import edu.uci.ics.cs.graspan.datastructures.AllPartitions;
import edu.uci.ics.cs.graspan.datastructures.LoadedPartitions;
import edu.uci.ics.cs.graspan.datastructures.LoadedVertexInterval;
import edu.uci.ics.cs.graspan.datastructures.NewEdgesList;
import edu.uci.ics.cs.graspan.datastructures.PartitionQuerier;
import edu.uci.ics.cs.graspan.datastructures.Vertex;
import edu.uci.ics.cs.graspan.dispatcher.GlobalParams;
import edu.uci.ics.cs.graspan.scheduler.SchedulerInfo;
import edu.uci.ics.cs.graspan.support.GraspanLogger;
import edu.uci.ics.cs.graspan.support.Utilities;

/**
 * This program loads partitions into the memory.
 * 
 * @author Aftab
 * 
 */
public class LoaderM {

	private static final int EDC_SIZE = GlobalParams.getEdcSize();
	private static final Logger logger = GraspanLogger.getLogger("LoaderM");

	public static Vertex[] vertices = null;
	public List<LoadedVertexInterval> intervals = new ArrayList<LoadedVertexInterval>();

	public static Vertex[] prevRoundVertices = null;
	List<LoadedVertexInterval> oldIntervals = null;

	private String baseFilename = "";
	private String reloadPlan = "";

	private String loadedIntStartOP = "";
	private String loadedIntEndOP = "";

	private int numParts = 0;

	/**
	 * Initializes the partition loader, reads in the partition allocation
	 * table, and reads in the edgedestcountInfo (Should be called only once.)
	 * 
	 * @param baseFilename
	 * @param numParts
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public LoaderM() throws NumberFormatException, IOException {

		this.baseFilename = GlobalParams.getBasefilename();
//		this.numParts = GlobalParams.getNumParts();
		this.reloadPlan = GlobalParams.getReloadPlan();

		// get the partition allocation table
		this.numParts = this.readPartAllocTable();

		// get the scheduling info
		this.readSchedulingInfo();

		// initialize variables for partition loading based on number of
		// partitions to load.
		preliminaryInit();

		// scheduler info test
		// SchedulerInfo.printData();
	}

	/**
	 * Initialize variables for partition loading only based on number of
	 * partitions to load.
	 */
	private void preliminaryInit() {

		int newPartsToLoad[] = new int[GlobalParams.getNumPartsPerComputation()];
		int loadedParts[] = new int[GlobalParams.getNumPartsPerComputation()];
		LinkedHashSet<Integer> partsToSave = new LinkedHashSet<Integer>();

		for (int i = 0; i < loadedParts.length; i++) {
			newPartsToLoad[i] = Integer.MIN_VALUE;
			loadedParts[i] = Integer.MIN_VALUE;
		}

		int loadedPartOutDegs[][] = new int[GlobalParams.getNumPartsPerComputation()][];
		int loadedPartEdges[][][] = new int[GlobalParams.getNumPartsPerComputation()][][];
		byte loadedPartEdgeVals[][][] = new byte[GlobalParams.getNumPartsPerComputation()][][];

		LoadedPartitions.setPartsToSave(partsToSave);
		LoadedPartitions.setNewParts(newPartsToLoad);
		LoadedPartitions.setLoadedParts(loadedParts);
		LoadedPartitions.setLoadedPartOutDegs(loadedPartOutDegs);
		LoadedPartitions.setLoadedPartEdges(loadedPartEdges);
		LoadedPartitions.setLoadedPartEdgeVals(loadedPartEdgeVals);

	}

	/**
	 * Loads partitions in the memory
	 * 
	 * @param partsToLoad
	 * @throws IOException
	 */
	public void loadParts(int[] partsToLoad) throws IOException {

		// save previous round's vertices and previous round's lvi
		if (vertices != null) {
			prevRoundVertices = new Vertex[vertices.length];
			// prevRoundVertices = vertices;
			System.arraycopy(vertices, 0, prevRoundVertices, 0, vertices.length);
			// List<LoadedVertexInterval> oldIntervals = new
			// ArrayList<LoadedVertexInterval>(
			// intervals);

			if (intervals.size() != 2) {
				logger.info("Warning: intervals size is " + intervals.size());
			}
			List<LoadedVertexInterval> oldIntervals = new ArrayList<LoadedVertexInterval>();
			for (int i = 0; i < intervals.size(); i++) {
				LoadedVertexInterval intv_to_copy = new LoadedVertexInterval(intervals.get(i).getFirstVertex(), intervals.get(i).getLastVertex(), intervals.get(i)
						.getPartitionId());
				intv_to_copy.setIndexStart(intervals.get(i).getIndexStart());
				intv_to_copy.setIndexEnd(intervals.get(i).getIndexEnd());
				intv_to_copy.setIsNewEdgeAdded(intervals.get(i).hasNewEdges());
				oldIntervals.add(intv_to_copy);
			}

			this.oldIntervals = oldIntervals;
//			logger.info("oldIntervals after creation: " + oldIntervals);
		}

//		loadedIntStartOP = "Loaded intervals at start of loading: ";
//		for (LoadedVertexInterval interval : intervals) {
//			loadedIntStartOP = loadedIntStartOP + interval.getPartitionId()+ " ";
//		}
//		logger.info(loadedIntStartOP);

//		String str = "";
//		for (int i = 0; i < partsToLoad.length; i++) {
//			str = str + partsToLoad[i] + " ";
//		}
//		logger.info("NEW COMPUTATION SET: Loading partitions : " + str + "...");

		// update newPartsToLoad
		updateNewPartsAndLoadedParts(partsToLoad);

		// logger.info("oldIntervals after  update newPartsToLoad: " + oldIntervals);

		// update loadedPartOutDegrees
		updateDegsOfPartsToLoad();

		// logger.info("oldIntervals after update loadedPartOutDegrees: " + oldIntervals);

		// initialize data structures of the partitions to load
		initVarsOfPartsToLoad();

		// logger.info("oldIntervals after init of data structures of the partitions to load: " + oldIntervals);

//		logger.info("Initialized data structures for partitions to load.");

		// fill the partition data structures
		fillVarsOfPartsToLoad();

//		int loadedPartOutDegs[][] = LoadedPartitions.getLoadedPartOutDegs();
//		int partEdges[][][] = LoadedPartitions.getLoadedPartEdges();
//		byte partEdgeVals[][][] = LoadedPartitions.getLoadedPartEdgeVals();
		int newParts[] = LoadedPartitions.getNewParts();

		// sorting the partitions
//		for (int i = 0; i < newParts.length; i++) {
//			for (int j = 0; j < PartitionQuerier.getNumUniqueSrcs(newParts[i]); j++) {
//				int low = 0;
//				int high = loadedPartOutDegs[i][j] - 1;
//				Utilities.quickSort(partEdges[i][j], partEdgeVals[i][j], low,
//						high);
//			}
//		}
//		logger.info("Sorted loaded partitions.");

		// reset newParts
		for (int i = 0; i < newParts.length; i++) {
			newParts[i] = Integer.MIN_VALUE;
		}

		// loaded partitions test
		// LoadedPartitions.printLoadedPartitions();

		// loaded parts degrees test
		// LoadedPartitions.printLoadedPartOutDegs();
		// System.exit(0);

		loadedIntEndOP = "Loaded intervals at end of loading: ";
		for (LoadedVertexInterval interval : intervals) {
			loadedIntEndOP = loadedIntEndOP + interval.getPartitionId() + " ";
		}
//		logger.info(loadedIntEndOP);

//		logger.info("vertices after loading is complete");
//		for (int i = 0; i < vertices.length; i++) {
//			logger.info(vertices[i].getVertexId() + ": "
//					+ Arrays.toString(vertices[i].getOutEdges()));
//		}
	}

	/**
	 * Gets the partition allocation table. (Should be called only once during
	 * first load)
	 * 
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	private int readPartAllocTable() throws NumberFormatException, IOException {
		List<int[]> list = new ArrayList<int[]>();

		/*
		 * Scan the partition allocation table file
		 */
		BufferedReader inPartAllocTabStrm = new BufferedReader(
				new InputStreamReader(new FileInputStream(new File(baseFilename + ".partAllocTable"))));
		String ln, tok[];

//		int i = 0;
		while ((ln = inPartAllocTabStrm.readLine()) != null) {
			tok = ln.split("\t");
			// store partition allocation table in memory
			int[] part = new int[2];
			part[0] = Integer.parseInt(tok[0]);
			part[1] = Integer.parseInt(tok[1]);
			list.add(part);
//			i++;
		}
		inPartAllocTabStrm.close();
		
		// initialize partAllocTable variable
		int partAllocTable[][] = new int[list.size()][2];

		for(int i = 0; i < list.size(); i++){
			partAllocTable[i] = list.get(i);
		}
		
		AllPartitions.setPartAllocTab(partAllocTable);
		
		return partAllocTable.length;
//		logger.info("Loaded " + baseFilename + ".partAllocTable");

	}

	/**
	 * Gets the Scheduling Info. (Should be called only once during first load)
	 * 
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	private void readSchedulingInfo() throws NumberFormatException, IOException {

		/*
		 * Scan the partSizes file
		 */
		readPartSizes();
//		logger.info("Loaded " + baseFilename + ".partSizes");
	

		/*
		 * Scan the edge destination counts file
		 */
		long[][] edgeDestCount = readEDC();
//		logger.info("Loaded " + baseFilename + ".edgeDestCounts");
		
		/*
		 * Update edcTwoWay 
		 */
		createTwoWayEdc(edgeDestCount);
	}

	private long[][] readEDC() throws FileNotFoundException, IOException {
		String ln;
		long edgeDestCount[][] = new long[EDC_SIZE][EDC_SIZE];
		BufferedReader inEdgeDestCountStrm = new BufferedReader(
				new InputStreamReader(new FileInputStream(new File(baseFilename	+ ".edgeDestCounts"))));

		int partA, partB;
		String[] tok;
		while ((ln = inEdgeDestCountStrm.readLine()) != null) {
			tok = ln.split("\t");
			partA = Integer.parseInt(tok[0]);
			partB = Integer.parseInt(tok[1]);
			// store edge destination counts in memory
			edgeDestCount[partA][partB] = Long.parseLong(tok[2]);
			
			// the following will store only percentages in a one way direction - we can't
			//store a 2 way direction percentage here because, edc[b][a] may not yet have been
			//updated
			//edcPercentages[partA][partB]=(double)edgeDestCount[partA][partB] / partSizes[partA][1];
			// a better option is to update edcPercentages after edc has been fully initialized, done below
		}
		SchedulerInfo.setEdgeDestCount(edgeDestCount);
		inEdgeDestCountStrm.close();
		return edgeDestCount;
	}

	private void readPartSizes() throws FileNotFoundException, IOException {
		String ln;
		long partSizes[][] = new long[numParts][2];
		BufferedReader inPartSizesStrm = new BufferedReader(
				new InputStreamReader(new FileInputStream(new File(baseFilename	+ ".partSizes"))));
		int j = 0;
		while ((ln = inPartSizesStrm.readLine()) != null) {
			// store partSizes in memory
			partSizes[j][0] = j;
			partSizes[j][1] = Long.parseLong(ln);
			j++;
		}
		SchedulerInfo.setPartSizes(partSizes);
		inPartSizesStrm.close();
	}

	private void createTwoWayEdc(long[][] edgeDestCount) {
		long edcTwoWay[][] = new long[EDC_SIZE][EDC_SIZE];
		for (int partA = 0; partA < numParts; partA++) {
			for (int partB = 0; partB < numParts; partB++) {
				if (partA == partB)
					edcTwoWay[partA][partB] = edgeDestCount[partA][partB];
				else
					edcTwoWay[partA][partB] = edgeDestCount[partA][partB] + edgeDestCount[partB][partA];
			}
		}
		SchedulerInfo.setEdcTwoWay(edcTwoWay);
	}


	public Vertex[] getVertices() {
		return vertices;
	}

	public List<LoadedVertexInterval> getIntervals() {
		return intervals;
	}

	/**
	 * Computes the next set of parts that are to be loaded in the memory.
	 * 
	 * @param partsToLoad
	 * @throws IOException
	 */
	private void updateNewPartsAndLoadedParts(int partsToLoad[])
			throws IOException {
		/*
		 * NOTE: At no point will partsToLoad be equal to loadedparts.
		 */

		// INCOMPLETE - SINCE RELOAD PLAN 1 IS THE WORST PLAN, WE SHALL
		// IGNORE THIS
//		if (this.reloadPlan.compareTo("RELOAD_PLAN_1") == 0) {
//			int[] newParts = LoadedPartitions.getNewParts();
//			newParts = partsToLoad;
//			LoadedPartitions.setNewParts(newParts);
//		}

		if (this.reloadPlan.compareTo("RELOAD_PLAN_2") == 0) {
			int[] loadedParts = LoadedPartitions.getLoadedParts();
			int[] newParts = LoadedPartitions.getNewParts();
			HashSet<Integer> partsToSaveByLoader = LoadedPartitions.getPartsToSave();
			HashSet<Integer> tempSet = new HashSet<Integer>();

			/*
			 * partid loading test 1/2 (comment loadedParts, newParts
			 * initialization above, change name of parameter partsToLoad above)
			 */
			// System.out.println("START");
			//
			// int[] loadedParts = { 8, 2, 3, Integer.MIN_VALUE, 6, 7 };
			// System.out.println("loadedParts");
			// for (int i = 0; i < loadedParts.length; i++)
			// System.out.print(loadedParts[i] + " ");
			// System.out.println();
			//
			// int[] partsToLoad = { 1, 3, 8, 7, 10, 11 };
			// System.out.println("partsToLoad");
			// for (int i = 0; i < partsToLoad.length; i++)
			// System.out.print(partsToLoad[i] + " ");
			// System.out.println();
			//
			// int[] newParts = { Integer.MIN_VALUE, Integer.MIN_VALUE,
			// Integer.MIN_VALUE, Integer.MIN_VALUE,
			// Integer.MIN_VALUE, Integer.MIN_VALUE };
			//
			// System.out.println("newParts");
			// for (int i = 0; i < newParts.length; i++)
			// System.out.print(newParts[i] + " ");
			// System.out.println();

			// 1. Get parts that are not part of the next computation and should
			// be saved.

			// 1.1. Get ids of all parts for next computation.
			for (int i = 0; i < partsToLoad.length; i++) {
				tempSet.add(partsToLoad[i]);
			}
			// 1.2. Add the ones not included for next computation to
			// partsToSave
			for (int i = 0; i < loadedParts.length; i++) {
				if (!tempSet.contains(loadedParts[i])
						&& loadedParts[i] != Integer.MIN_VALUE) {
					partsToSaveByLoader.add(loadedParts[i]);
				}
			}

			// test partsToSave
			if (partsToSaveByLoader.size() == 0) {
//				logger.info("No Parts to save by Loader");
			} else {
//				logger.info("Parts to save by Loader: " + partsToSaveByLoader);
			}

			// 2. Save PartsSet

			// 2.1. save partitions not in the next round
			for (Integer partitionId : partsToSaveByLoader)
			{	long writeStart = System.currentTimeMillis();
				storePart(getVertices(), getIntervals(), partitionId);
//				EngineM.getIO_outputStrm().println("write," + Utilities.getDurationInHMS(System.currentTimeMillis() - writeStart) );
//				logger.info("output.IO||"+"write," + Utilities.getDurationInHMS(System.currentTimeMillis() - writeStart) );
				logger.info("output.IO||"+"write," + (System.currentTimeMillis() - writeStart) );
			}

			// 2.2. save degrees of partitions not in the next round
			for (Integer partitionId : partsToSaveByLoader)
			{
				long writeStart = System.currentTimeMillis();
				storePartDegs(getVertices(), getIntervals(), partitionId);
//				EngineM.getIO_outputStrm().println("write," + Utilities.getDurationInHMS(System.currentTimeMillis() - writeStart) );
//				logger.info("output.IO||"+"write," + Utilities.getDurationInHMS(System.currentTimeMillis() - writeStart) );
				logger.info("output.IO||"+"write," + (System.currentTimeMillis() - writeStart) );
			}
				
			// 2.3. Remove saved partitions from LoadedVertexIntervals
			for (int i = 0; i < intervals.size(); i++) {
				if (partsToSaveByLoader.contains(intervals.get(i).getPartitionId())) {
					intervals.remove(i);
					// reset i
					i--;
				}
			}

			tempSet.clear();

			// 3. Update newParts and loadedParts.

			// 3.1. Get ids of all parts currently loaded
			for (int i = 0; i < loadedParts.length; i++) {
				tempSet.add(loadedParts[i]);
			}

			String loadedPartsOP = "Loaded Parts at load start (loadedParts): ";
			for (int i = 0; i < loadedParts.length; i++)
				loadedPartsOP = loadedPartsOP + loadedParts[i] + " ";
//			logger.info(loadedPartsOP);

			// 3.2. Get ids of partitions not loaded and store them in the
			// positions of partitions that are to be saved
			for (int i = 0; i < partsToLoad.length; i++) {
				// if the partition is not already loaded
				if (!tempSet.contains(partsToLoad[i])) {
					// find the partition that is loaded but no longer required
					// (i.e. in savePartsSet)
					for (int j = 0; j < loadedParts.length; j++) {
						if (loadedParts[j] == Integer.MIN_VALUE) {
							// store the new id in loadedParts in place of the
							// partition to save
							loadedParts[j] = partsToLoad[i];

							// store the new id in the corresponding location in
							// newParts
							newParts[j] = partsToLoad[i];

							break;
						}
						if (partsToSaveByLoader.contains(loadedParts[j])) {

							// logger.info("see here now" + loadedParts[j]);

							// store the new id in loadedParts in place of the
							// partition to save
							loadedParts[j] = partsToLoad[i];

							// store the new id in the corresponding location in
							// newParts
							newParts[j] = partsToLoad[i];

							break;
						}
					}
				}
			}

			partsToSaveByLoader.clear();

			/*
			 * partid loading test 2/2
			 */
			String newPartsOP = "New partitions: ";
			for (int i = 0; i < newParts.length; i++)
				newPartsOP = newPartsOP + "" + newParts[i] + " ";
//			logger.info(newPartsOP);

			String partsNxtCompOP = "Parts for next computatn (loadedParts): ";
			for (int i = 0; i < loadedParts.length; i++)
				partsNxtCompOP = partsNxtCompOP + "" + loadedParts[i] + " ";
//			logger.info(partsNxtCompOP);
			// System.out.println();
			// System.out.println("partsToLoad");
			// for (int i = 0; i < partsToLoad.length; i++)
			// System.out.print(partsToLoad[i] + " ");
			// System.out.println();
			// System.exit(0);

		}
	}

	/**
	 * Updates the degrees of the source vertices of the partitions that are to
	 * be loaded.
	 * 
	 * @param baseFilename
	 * @param partsToLoad
	 * @throws IOException
	 * @throws NumberFormatException
	 */
	private void updateDegsOfPartsToLoad() throws IOException {

		int[] newParts = LoadedPartitions.getNewParts();
		int[][] partOutDegs = LoadedPartitions.getLoadedPartOutDegs();

		/*
		 * Initialize the degrees array for each new partition to load. We shall
		 * not reinitialize the degrees of the partitions that have already been
		 * loaded (This does not apply for RELOAD_PLAN_1).
		 */

		for (int i = 0; i < newParts.length; i++) {
			if (newParts[i] != Integer.MIN_VALUE) {
				// initialize Dimension 2 (Total no. of Unique SrcVs for a Partition)
				partOutDegs[i] = new int[PartitionQuerier.getNumUniqueSrcs(newParts[i])];
				// remember to use this only for loading partitions that aren't currently loaded.
			}
		}

		long readStart = System.currentTimeMillis();
		/*
		 * Scan degrees file of each partition
		 */
		for (int i = 0; i < newParts.length; i++) {
			if (newParts[i] != Integer.MIN_VALUE) {
				BufferedReader outDegInStrm = new BufferedReader(new InputStreamReader(new FileInputStream(new File(baseFilename + ".partition." + newParts[i]+ ".degrees"))));
				String ln;
				while ((ln = outDegInStrm.readLine()) != null) {

					String[] tok = ln.split("\t");

					// get the srcVId and degree
					int srcVId = Integer.parseInt(tok[0]);
					int deg = Integer.parseInt(tok[1]);
					try {
						partOutDegs[i][srcVId- PartitionQuerier.getFirstSrc(newParts[i])] = deg;
					} catch (Exception e) {
						logger.info("ERROR!: " + srcVId + " "+ PartitionQuerier.getFirstSrc(newParts[i]));
					}
					// this will be later updated in processParts() of
					// ComputedPartProcessor if new edges are added for this
					// source vertex during computation.
				}
				outDegInStrm.close();

//				logger.info("Loaded " + baseFilename + ".partition."
//						+ newParts[i] + ".degrees");
			}
		}
//		EngineM.getIO_outputStrm().println("read," + Utilities.getDurationInHMS(System.currentTimeMillis() - readStart) );
//		logger.info("output.IO||"+"read," + Utilities.getDurationInHMS(System.currentTimeMillis() - readStart) );
		logger.info("output.IO||"+"read," + (System.currentTimeMillis() - readStart) );
	}

	/**
	 * Gets the degrees of the source vertices of the partitions that are to be
	 * loaded. (Deprecated- this method reads the degrees file of the entire
	 * graph.)
	 * 
	 * @param baseFilename
	 * @param partsToLoad
	 * @throws IOException
	 * @throws NumberFormatException
	 */
	@SuppressWarnings("unused")
	private void getDegrees(String baseFilename, int[] partsToLoad)
			throws NumberFormatException, IOException {

		/*
		 * Initialize the degrees array for each partition
		 */

		// initialize Dimension 1 (Total no. of Partitions)
		int[][] partOutDegs = new int[partsToLoad.length][];

		for (int i = 0; i < partsToLoad.length; i++) {

			// initialize Dimension 2 (Total no. of Unique SrcVs for a
			// Partition)
			partOutDegs[i] = new int[PartitionQuerier.getNumUniqueSrcs(partsToLoad[i])];
		}

		/*
		 * Scan the degrees file
		 */
		BufferedReader outDegInStrm = new BufferedReader(new InputStreamReader(
				new FileInputStream(new File(baseFilename + ".degrees"))));

		System.out.print("Reading degrees file (" + baseFilename + ".degrees) to obtain degrees of source vertices in partitions to load");

		String ln;
		while ((ln = outDegInStrm.readLine()) != null) {

			String[] tok = ln.split("\t");

			// get the srcVId and degree
			int srcVId = Integer.parseInt(tok[0]);
			int deg = Integer.parseInt(tok[1]);

			for (int i = 0; i < partsToLoad.length; i++) {

				// check if the srcVId belongs to this partition
				if (!PartitionQuerier.inPartition(srcVId, partsToLoad[i])) {
					continue;
				} else {
					try {
						partOutDegs[i][srcVId
								- PartitionQuerier.getFirstSrc(partsToLoad[i])] = deg;
					} catch (ArrayIndexOutOfBoundsException e) {
					}
				}
			}
		}
		LoadedPartitions.setLoadedPartOutDegs(partOutDegs);
		outDegInStrm.close();
		System.out.println("Done");
	}

	/**
	 * Initializes data structures of the partitions to load
	 */
	private void initVarsOfPartsToLoad() {

		int[][] partOutDegs = LoadedPartitions.getLoadedPartOutDegs();
		int[] loadedParts = LoadedPartitions.getLoadedParts();
		int[] newParts = LoadedPartitions.getNewParts();

		// initializing new data structures
		int totalNumVertices = 0;
		for (int i = 0; i < loadedParts.length; i++) {
			totalNumVertices += PartitionQuerier.getNumUniqueSrcs(loadedParts[i]);
		}
		vertices = new Vertex[totalNumVertices];

		// System.out.println("Loaded Vertex Intervals size");
		// System.out.println(intervals.size());
		// System.out.println(vertices.length);

		int partEdges[][][] = LoadedPartitions.getLoadedPartEdges();
		byte partEdgeVals[][][] = LoadedPartitions.getLoadedPartEdgeVals();

		// reassigning the partEdges and partEdgeVAls only for the newly loaded
		// partitions in this round
		for (int i = 0; i < newParts.length; i++) {
			if (newParts[i] != Integer.MIN_VALUE) {

				// initialize Dimension 2 (Total no. of Unique SrcVs for a
				// Partition)
				partEdges[i] = new int[PartitionQuerier.getNumUniqueSrcs(newParts[i])][];
				partEdgeVals[i] = new byte[PartitionQuerier.getNumUniqueSrcs(newParts[i])][];

				for (int j = 0; j < PartitionQuerier.getNumUniqueSrcs(newParts[i]); j++) {

					// initialize Dimension 3 (Total no. of Out-edges for a
					// SrcV)
					partEdges[i][j] = new int[partOutDegs[i][j]];
					partEdgeVals[i][j] = new byte[partOutDegs[i][j]];

				}
			}
		}

		// set vertices data structure
		int vertexIdx = 0;
		for (int i = 0; i < loadedParts.length; i++) {
			for (int j = 0; j < PartitionQuerier.getNumUniqueSrcs(loadedParts[i]); j++) {
				int vertexId = PartitionQuerier.getActualIdFrmPartArrIdx(j, loadedParts[i]);
				vertices[vertexIdx] = new Vertex(vertexIdx, vertexId, partEdges[i][j], partEdgeVals[i][j]);
				vertexIdx++;
			}
		}

	}

	/**
	 * Reads the partition files and stores them in arrays
	 * 
	 * @param partInputStream
	 * @throws IOException
	 */
	private void fillVarsOfPartsToLoad() throws IOException {

		int[] newParts = LoadedPartitions.getNewParts();
		int[] loadedparts = LoadedPartitions.getLoadedParts();
		int[][][] partEdges = LoadedPartitions.getLoadedPartEdges();
		byte[][][] partEdgeVals = LoadedPartitions.getLoadedPartEdgeVals();

		long readStart = System.currentTimeMillis();
		for (int i = 0; i < newParts.length; i++) {
			if (newParts[i] != Integer.MIN_VALUE) {

				DataInputStream partInStrm = new DataInputStream(new BufferedInputStream(new FileInputStream(baseFilename + ".partition." + newParts[i])));

				// stores the position of last filled edge (destV) and the edge
				// val in partEdges and partEdgeVals for a source vertex for a partition
				int[] lastAddedEdgePos = new int[PartitionQuerier.getNumUniqueSrcs(newParts[i])];
				for (int j = 0; j < lastAddedEdgePos.length; j++) {
					lastAddedEdgePos[j] = -1;
				}

				while (partInStrm.available() != 0) {
					{
						try {
							// get srcVId
							int src = partInStrm.readInt();

							// get corresponding arraySrcVId of srcVId
							int arraySrcVId = src - PartitionQuerier.getFirstSrc(newParts[i]);

							// get count (number of destVs from srcV in the current list of the partition file)
							int count = partInStrm.readInt();

							// get dstVId & edgeVal and store them in the corresponding arrays
							for (int j = 0; j < count; j++) {

								// dstVId
								partEdges[i][arraySrcVId][lastAddedEdgePos[arraySrcVId] + 1] = partInStrm.readInt();

//								// edgeVal
								partEdgeVals[i][arraySrcVId][lastAddedEdgePos[arraySrcVId] + 1] = partInStrm.readByte();

								// increment the last added position for this row
								lastAddedEdgePos[arraySrcVId]++;
							}

						} catch (Exception exception) {
							break;
						}
					}
				}

				partInStrm.close();

//				logger.info("Loaded " + baseFilename + ".partition." + newParts[i]);
			}
		}
		
//		EngineM.getIO_outputStrm().println("read," + Utilities.getDurationInHMS(System.currentTimeMillis() - readStart) );
//		logger.info("output.IO||"+"read," + Utilities.getDurationInHMS(System.currentTimeMillis() - readStart) );
		logger.info("output.IO||"+"read," + (System.currentTimeMillis() - readStart) );

		// test 
		// logger.info("partEdges content after loading:");
		// int[][] partOutDegs = LoadedPartitions.getLoadedPartOutDegs();
		// String str = "";
		// for (int i = 0; i < newParts.length; i++) {
		// logger.info("Partition number:" + newParts[i]);
		// for (int j = 0; j < PartitionQuerier.getNumUniqueSrcs(newParts[i]);
		// j++) {
		// logger.info("Vertex number (array id - not actual id): " + j);
		// str = "";
		// for (int k = 0; k < partOutDegs[i][j]; k++) {
		// str = str + partEdges[i][j][k] + " ";
		// }
		// logger.info(str);
		// }
		// }

		int indexSt = 0;
		int indexEd = 0;
		int partId = 0;
		// set the new indexes
		ArrayList<Integer> intervalIndices = new ArrayList<Integer>();
		for (int i = 0; i < loadedparts.length; i++) {
			partId = loadedparts[i];
			intervalIndices.add(indexSt);
			indexEd = indexSt + PartitionQuerier.getNumUniqueSrcs(partId) - 1;
			intervalIndices.add(indexEd);
			indexSt = indexEd + 1;
		}

//		logger.info("OldIntervals before starting lvi updates: " + oldIntervals);

		boolean alreadyLoaded;
		int intrvlIndxMarker = 0;
		for (int i = 0; i < loadedparts.length; i++) {
			alreadyLoaded = false;
			partId = loadedparts[i];

			// lvi update for partitions ALREADY loaded
			for (LoadedVertexInterval interval : intervals) {
				if (partId == interval.getPartitionId()) {
					alreadyLoaded = true;

					interval.setIndexStart(intervalIndices.get(intrvlIndxMarker));
					intrvlIndxMarker++;

					interval.setIndexEnd(intervalIndices.get(intrvlIndxMarker));
					intrvlIndxMarker++;

//					logger.info("Updated interval parameters for partition: "+ partId);
					break;
				}
			}

			// lvi update for new partitions that are to be loaded
			if (!alreadyLoaded) {
				LoadedVertexInterval interval = new LoadedVertexInterval(PartitionQuerier.getFirstSrc(partId), PartitionQuerier.getLastSrc(partId), partId);

				interval.setIndexStart(intervalIndices.get(intrvlIndxMarker));
				intrvlIndxMarker++;

				interval.setIndexEnd(intervalIndices.get(intrvlIndxMarker));
				intrvlIndxMarker++;

				intervals.add(interval);

//				logger.info("Added new interval for part " + partId);
			}

		}

//		logger.info("OldIntervals before after lvi updates: " + oldIntervals);

		if (oldIntervals != null) {
			int oldIntvIdxSt = 0, oldIntvIdxEnd = 0, newIntvIdxSt = 0, newIntvIdxEnd = 0;
			for (int i = 0; i < intervals.size(); i++) {
				for (int j = 0; j < oldIntervals.size(); j++) {
					if (intervals.get(i).getPartitionId() == oldIntervals.get(j).getPartitionId()) {

						// preserve the edges generated in previous iteration using info from old Intervals
						newIntvIdxSt = intervals.get(i).getIndexStart();
						newIntvIdxEnd = intervals.get(i).getIndexEnd();
						oldIntvIdxSt = oldIntervals.get(j).getIndexStart();
						oldIntvIdxEnd = oldIntervals.get(j).getIndexEnd();

//						String s2 = "";
//						for (int u = 0; u < prevRoundVertices.length; u++) {
//							s2 = s2 + " " + prevRoundVertices[u].getVertexId();
//						}

//						logger.info("All prevRoundvertices in memory during loading: \n"
//								+ s2);
//						logger.info("oldIntvIdxSt: " + oldIntvIdxSt + " oldIntvIdxEnd: " + oldIntvIdxEnd + " oldIntervals partId: " + oldIntervals.get(j).getPartitionId());
//						logger.info("OldIntervals: " + oldIntervals);
//						logger.info("Current Intervals:" + intervals);

						// this preservation strategy assumes that when an
						// interval is repartitioned it is immediately saved to disk by
						// the  computed part processor

						if ((oldIntvIdxSt - oldIntvIdxEnd) != (newIntvIdxSt - newIntvIdxEnd)) {
							logger.info("ERROR: number of vertices in an interval has changed!");
							System.exit(0);
						}

						int l = oldIntvIdxSt;
						for (int k = newIntvIdxSt; k < newIntvIdxEnd + 1; k++) {
							try {
								vertices[k] = prevRoundVertices[l];
								l++;
							} catch (ArrayIndexOutOfBoundsException e) {
								logger.info("" + k + " " + vertices.length);
								logger.info("" + l + " " + prevRoundVertices.length);
							}
						}
					}
				}
			}
		}
		// COMMENT THIS AS WE SHALL NOT PRESERVE DEGREES, WE SHALL
		// COMPUTE
		// IT EVERYTIME
		// update vertices degrees
		// for (int i = 0; i < vertices.length; i++) {
		// vertices[i].setCombinedDeg(vertices[i].getNumOutEdges());
		// }

		// test - COMMENT THIS CHUNK
		// logger.info("lvi content after loading:");

		// for (int i = 0; i < loadedparts.length; i++) {
		//
		// }
	}

	/**
	 * Stores a partition to disk.
	 * 
	 * @param vertices
	 * @param newEdgesLL
	 * @param intervals
	 * @param partitionId
	 * @throws IOException
	 */
	private static void storePart(Vertex[] vertices,
			List<LoadedVertexInterval> intervals, Integer partitionId)
			throws IOException {

//		logger.info("Updating " + GlobalParams.baseFilename + ".partition." + partitionId);
		
//		logger.info("vertices before storing");
//		for (int i = 0; i < vertices.length; i++) {
//			logger.info(vertices[i].getVertexId() + ": "
//					+ Arrays.toString(vertices[i].getOutEdges()));
//		}

		for (int i = 0; i < intervals.size(); i++) {

			// locate the required interval in "vertices", and if it has new  edges added
			if (partitionId == intervals.get(i).getPartitionId() && intervals.get(i).hasNewEdges()) {

//				logger.info("partition ID : " + partitionId);
				// clear current file
				DataOutputStream partOutStrm = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(GlobalParams.baseFilename + ".partition." + partitionId, false)));
				partOutStrm.close();

				partOutStrm = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(GlobalParams.baseFilename + ".partition." + partitionId, true)));

				int srcVId, destVId, count;
				int edgeValue;

				// scan each vertex in this interval in "vertices" datastructure
				for (int j = intervals.get(i).getIndexStart(); j < intervals.get(i).getIndexEnd() + 1; j++) {
					count = vertices[j].getNumOutEdges();

					if (count == 0) {
						continue;
					}

					// write the srcId
					srcVId = vertices[j].getVertexId();
					partOutStrm.writeInt(srcVId);

					// write the count
					partOutStrm.writeInt(count);

					// scan each edge (original edge) in list of each vertex in this interval
					for (int k = 0; k < vertices[j].getNumOutEdges(); k++) {

						// write the destId-edgeValue pair
//						if (vertices[j].getOutEdges().length > 0) {
						if (vertices[j].getNumOutEdges() > 0) {	
							if (vertices[j].getOutEdge(k) == -1)
								break;
							destVId = vertices[j].getOutEdge(k);
							edgeValue = vertices[j].getOutEdgeValue(k);
							partOutStrm.writeInt(destVId);
							partOutStrm.writeByte(edgeValue);
//							if (destVId == 0) {
//								logger.info(srcVId + "\t" + destVId + "\t" + edgeValue);
//							}

						}

					}

				}
				partOutStrm.close();
			}
		}
	}

	/**
	 * Stores degrees of a partition.
	 * 
	 * @param vertices
	 * @param intervals
	 * @param partitionId
	 * @throws IOException
	 */
	public static void storePartDegs(Vertex[] vertices,
			List<LoadedVertexInterval> intervals, Integer partitionId)
			throws IOException {

//		logger.info("Updating " + GlobalParams.baseFilename + ".partition." + partitionId + ".degrees");

		for (int i = 0; i < intervals.size(); i++) {

			// locate the required interval in "vertices", and if it has new edges added
			if (partitionId == intervals.get(i).getPartitionId() && intervals.get(i).hasNewEdges()) {

				// clear current degrees file
				PrintWriter partDegOutStrm = new PrintWriter
						(new BufferedWriter(new FileWriter( GlobalParams.baseFilename + ".partition." + partitionId + ".degrees", false)));
				partDegOutStrm.close();

				partDegOutStrm = new PrintWriter(
						new BufferedWriter(new FileWriter(GlobalParams.baseFilename + ".partition." + partitionId + ".degrees", true)));

				int srcVId, deg;

				// scan each vertex in this interval in "vertices" datastructure
				for (int j = intervals.get(i).getIndexStart(); j < intervals.get(i).getIndexEnd() + 1; j++) {

					// get srcId and deg
					srcVId = vertices[j].getVertexId();
					deg = vertices[j].getNumOutEdges();
					if (deg == 0)
						continue;
					partDegOutStrm.println(srcVId + "\t" + deg);

				}
				partDegOutStrm.close();
			}
		}

	}
}
