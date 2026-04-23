/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.power.models;

/**
 * The power model of an HP ProLiant ML110 G3 (1 x [Pentium D930 3000 MHz, 2 cores], 4GB).<br/>
 * <a href="http://www.spec.org/power_ssj2008/results/res2011q1/power_ssj2008-20110127-00342.html">
 * http://www.spec.org/power_ssj2008/results/res2011q1/power_ssj2008-20110127-00342.html</a>
 *
 * <br/>If you are using any algorithms, policies or workload included in the power package please cite
 * the following paper:<br/>
 *
 * <ul>
 * <li><a href="http://dx.doi.org/10.1002/cpe.1867">Anton Beloglazov, and Rajkumar Buyya, "Optimal Online Deterministic Algorithms and Adaptive
 * Heuristics for Energy and Performance Efficient Dynamic Consolidation of Virtual Machines in
 * Cloud Data Centers", Concurrency and Computation: Practice and Experience (CCPE), Volume 24,
 * Issue 13, Pages: 1397-1420, John Wiley & Sons, Ltd, New York, USA, 2012</a>
 * </ul>
 *
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 3.0
 */
public class PowerModelSpecPowerHpProLiantMl110G3PentiumD930 extends PowerModelSpecPower {
	/**
         * The power consumption according to the utilization percentage.
         * @see #getPowerData(int)
	     * 	 https://www.spec.org/power_ssj2008/results/res2011q1/power_ssj2008-20110127-00342.html
         */
	private final double[] power = { 105, 112, 118, 125, 131, 137, 147, 153, 157, 164, 169 };
	private final double[] peff = { 0, 47.9, 89.4, 128, 160, 191, 218, 241, 268, 285, 309 };
	private final double PeakPower = 169;//峰值能耗
	private final double PeakPeff = 309;//峰值效能比
	private final double pp_CPU_util = 1.0;//峰值效能比下的CPU利用率

	@Override
	protected double getPowerData(int index) {
		return power[index];
	}

	@Override
	protected double getPeffData(int index) {return peff[index];}

	@Override
	public final double[] getPowerList() {return power;}

	@Override
	public final double[] getPeffList() {return peff;}

	@Override
	public double getPp_CPU_tuil(){return pp_CPU_util;}

	@Override
	public double getPeakPower() {return PeakPower;}

	@Override
	public double getPeakPeff() {return PeakPeff;}
}
