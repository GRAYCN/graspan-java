package edu.uci.ics.cs.gDTCpreproc.preprocessing;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

import edu.uci.ics.cs.gDTCpreproc.ChiLogger;
import edu.uci.ics.cs.gDTCpreproc.datablocks.GenericIntegerConverter;

public class PartitionGenerator<VertexValueType, EdgeValueType> {

	public enum GraphInputFormat {
		EDGELIST, ADJACENCY, MATRIXMARKET
	};

	private String baseFilename;
	private int nParts;
	private int numEdges;
	private LinkedHashMap<Long, Integer> vOutDegs;

	private DataOutputStream[] shovelStreams;

	private GenericIntegerConverter vIdtoBytes;
	private GenericIntegerConverter edgeValtoBytes;

	private static final Logger logger = ChiLogger.getLogger("fast-sharder");

	/**
	 * Constructor
	 * 
	 * @param baseFilename
	 *            input-file
	 * @param nParts
	 *            the number of partitions to be created
	 * @param edgeValConverter
	 *            translator byte-arrays to/from edge-value
	 * @throws IOException
	 *             if problems reading the data
	 */
	public PartitionGenerator(String baseFilename, int nParts, GenericIntegerConverter vIdtoBytes,
			GenericIntegerConverter edgeValConverter) throws IOException {
		this.baseFilename = baseFilename;
		this.nParts = nParts;
		this.vIdtoBytes = vIdtoBytes;
		this.edgeValtoBytes = edgeValConverter;

		/**
		 * the edges are "shoveled" to partitions.
		 */
		shovelStreams = new DataOutputStream[nParts];
		for (int i = 0; i < nParts; i++) {

			/*
			 * ah46. Empty "shovel" files are created"
			 */
			shovelStreams[i] = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(shovelFilename(i))));
		}
	}

	/*
	 * ah46. separate methods for filenames
	 */
	private String shovelFilename(int i) {
		return baseFilename + ".shovel." + i;
	}

	/**
	 * Adds an edge to the preprocessing.
	 * 
	 * @param src
	 * @param dest
	 * @param edgeValue
	 * @throws IOException
	 */
	public void addEdge(int src, int dest, int edgeValue, long partMax) throws IOException {// ah46

		addToShovel(1, src, dest, edgeValue);

		ArrayList<Long> al = new ArrayList<Long>();
		LinkedHashMap<Long, ArrayList> edgeDistributorTable = new LinkedHashMap<Long, ArrayList>();
		int edtItemSize = edgeDistributorTable.get((long) 124).size();
		al = edgeDistributorTable.get((long) 124);

		if (edtItemSize != 0) {
			/*
			 * edge with same source already exists in the EDT
			 */
			if (edtItemSize < partMax) {// you can still add edges to the list
										// for this source vertex
				if (edgeDistributorTable.size() < nParts) {//
					al.add((long) 456);
					edgeDistributorTable.put((long) 124, al);
				}
			} else {// edge list for this source vertex is full.

			}
		}

	}

	private byte[] valueTemplate;

	private void addToShovel(int part, int src, int dest, int edgeValue) throws IOException {// ah46

		// The values being stored
		// System.out.print(String.valueOf(part)+" "+String.valueOf(src)+"
		// "+String.valueOf(dest)+" ");
		// System.out.print(edgeValue);
		// System.out.println();
		// System.exit(0);
		// System.out.print(()value);

		DataOutputStream strm = shovelStreams[part];

		valueTemplate = new byte[vIdtoBytes.getSize()];

		vIdtoBytes.setValue(valueTemplate, src);
		strm.write(valueTemplate);

		vIdtoBytes.setValue(valueTemplate, dest);
		strm.write(valueTemplate);

		valueTemplate = new byte[edgeValtoBytes.getSize()];

		edgeValtoBytes.setValue(valueTemplate, edgeValue);
		strm.write(valueTemplate);

		// edgeValtoBytes.setValue(valueTemplate, edgeValue);
		// strm.writeLong(packEdges(src, dest));
		// if (edgeValtoBytes != null) {
		// edgeValtoBytes.setValue(valueTemplate, edgeValue);
		// }

	}

	/**
	 * generates the partitions
	 * 
	 * @param inputStream
	 * @param format
	 *            graph input format
	 * @throws IOException
	 */
	public void pgen(InputStream inputStream) throws IOException {
		BufferedReader ins = new BufferedReader(new InputStreamReader(inputStream));
		long partMax = numEdges / nParts;
		String ln;
		long lineNum = 0;
		while ((ln = ins.readLine()) != null) {
			if (!ln.startsWith("#")) {
				lineNum++;
				if (lineNum % 100000 == 0)
					logger.info("Reading line: " + lineNum);
				String[] tok = ln.split("\t");

				/* Edge list: <src> <dst> <value> */
				this.addEdge(Integer.parseInt(tok[0]), Integer.parseInt(tok[1]), Integer.parseInt(tok[2]), partMax);
			}
		}
	}

	/**
	 * Scans the entire graph and counts the out degrees of all the vertices.
	 * This is the Preliminary Scan 
	 * 
	 * @param inputStream
	 * @param format
	 * @throws IOException
	 */
	public void genDegrees(InputStream inputStream) throws IOException {
		BufferedReader ins = new BufferedReader(new InputStreamReader(inputStream));
		String ln;
		long numEdges = 0;
		LinkedHashMap<Long, Integer> vOutDegs = new LinkedHashMap<Long,Integer>();
		
		while ((ln = ins.readLine()) != null) {
			if (!ln.startsWith("#")) {
				String[] tok = ln.split("\t");

				long src = Long.parseLong(tok[0]);
				
				if (!vOutDegs.containsKey(src)){
					vOutDegs.put(src, 1);
				}
				else{
					vOutDegs.put(src, vOutDegs.get(src)+1);
				}
				
				numEdges++;
				if (numEdges % 100000 == 0)
					logger.info("Reading line: " + numEdges);
			}
		}
		numEdges = this.numEdges;
		vOutDegs=this.vOutDegs;
	}
	
	/*
	 * Allocate vertices to partitions.
	 * 
	 */
	public void vAlloc(){
		
	}

}
