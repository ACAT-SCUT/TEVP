package org.cloudbus.cloudsim.experiment.ComparingExp;

import org.cloudbus.cloudsim.experiment.Constants;
import org.cloudbus.cloudsim.experiment.PlanetLabRunner;

import java.io.IOException;
import java.net.URLDecoder;

/**
 * A simulation of a heterogeneous power aware data center that applies the Inter Quartile Range
 * (IQR) VM allocation policy and Minimum Utilization (MU) VM selection policy.
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
public class IqrMu {

	/**
	 * The main method.
	 * 
	 * @param args the arguments
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void main(String[] args) throws IOException {
		boolean enableOutput = false;
		boolean outputToFile = false;
		String inputFolder = IqrMu.class.getClassLoader().getResource("workload/Mix").getPath();
		String outputFolder = "output";
		inputFolder = URLDecoder.decode(inputFolder, "UTF-8");//解决中文路径问题

		Constants.vmAllocationPolicy = "iqr"; // Inter Quartile Range (IQR) VM allocation policy
		Constants.vmSelectionPolicy = "mu"; // Minimum Utilization (MU) VM selection policy
		String parameter = "1.5"; // the safety parameter of the IQR policy

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
