package org.cloudbus.cloudsim.experiment.ComparingExp;

import org.cloudbus.cloudsim.experiment.Constants;
import org.cloudbus.cloudsim.experiment.PlanetLabRunner;

import java.io.IOException;
import java.net.URLDecoder;

/**
 * A simulation of a heterogeneous power aware data center that applies the Static Threshold (THR)
 * VM allocation policy and Random Selection (RS) VM selection policy.
 * 
 * The remaining configuration parameters are in the Constants and RandomConstants classes.
 * 
 * If you are using any algorithms, policies or workload included in the power package please cite
 * the following paper:
 * 
 * Anton Beloglazov, and Rajkumar Buyya, "Optimal Online Deterministic Algorithms and Adaptive
 * Heuristics for Energy and Performance Efficient Dynamic Consolidation of Virtual Machines in
 * Cloud Data Centers", Concurrency and Computation: Practice and Experience (CCPE), Volume 24,
 * Issue 13, Pages: 1397-1420, John Wiley & Sons, Ltd, New York, USA, 2012
 * 
 * @author Anton Beloglazov
 * @since Jan 5, 2012
 */
public class ThrRs {

	/**
	 * The main method.
	 * 
	 * @param args the arguments
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void main(String[] args) throws IOException {
		boolean enableOutput = false;
		boolean outputToFile = false;
		String inputFolder = ThrRs.class.getClassLoader().getResource("workload/planetlab").getPath();
		String outputFolder = "output";
		inputFolder = URLDecoder.decode(inputFolder, "UTF-8");//解决中文路径问题

		Constants.vmAllocationPolicy = "thr"; // Static Threshold (THR) VM allocation policy
		Constants.vmSelectionPolicy = "rs"; // Random Selection (RS) VM selection policy
		String parameter = "0.8"; // the static utilization threshold

		new PlanetLabRunner(
				enableOutput,
				outputToFile,
				inputFolder,
				outputFolder,
				Constants.workload,
				Constants.vmAllocationPolicy,
				Constants.vmSelectionPolicy,
				parameter);
	}

}
