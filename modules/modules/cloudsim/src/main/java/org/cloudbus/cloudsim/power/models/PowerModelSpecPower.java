/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.power.models;

/**
 * The abstract class of power models created based on data from 
 * <a href="http://www.spec.org/power_ssj2008/">SPECpower benchmark</a>.
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

/**该类用于设置和获取不同类型服务器，在不同CPU利用率下的能耗，采用的依然是分段线性模型*/
public abstract class PowerModelSpecPower implements PowerModel {
	@Override
	public double getPower(double utilization) throws IllegalArgumentException {
		if (utilization < 0 || utilization > 1) {
			throw new IllegalArgumentException("Utilization value must be between 0 and 1");
		}
		if(utilization==0) return 0;//util==0的话默认没有开机
		//能以10%为单位
		if ((utilization*10) % 1 == 0) {//Java小数取模会出0.01的误差，不知道为什么
			return getPowerData((int) (utilization * 10));
		}
		int utilization1 = (int) Math.floor(utilization * 10);//向下取整
		int utilization2 = (int) Math.ceil(utilization * 10);//向上取整
		double power1 = getPowerData(utilization1);
		double power2 = getPowerData(utilization2);
		double delta = power2 - power1 ;
		//这是因为power1+delta*(0.23-2/10==0.03)，还要*10才对
		double power = power1 + delta * (utilization - (double) utilization1 / 10) * 10;
		return power;
	}

	@Override
	public double getPeff(double utilization) throws IllegalArgumentException{
		if (utilization < 0 || utilization > 1) {
			throw new IllegalArgumentException("Utilization value must be between 0 and 1");
		}
		//能以10%为单位
		if ((utilization*10) % 1 == 0) {
			return getPeffData((int) (utilization * 10));
		}
		int utilization1 = (int) Math.floor(utilization * 10);//向下取整
		int utilization2 = (int) Math.ceil(utilization * 10);//向上取整
		double peff1 = getPeffData(utilization1);
		double peff2 = getPeffData(utilization2);
		double delta = peff2 - peff1;
		double peff = peff1 + delta * (utilization - (double) utilization1 / 10) * 10;
		return peff;
	}

	/**
	 * Gets the power consumption for a given utilization percentage.
	 *
	 * @param index the utilization percentage in the scale from [0 to 10],
	 * where 10 means 100% of utilization.
	 * @return the power consumption for the given utilization percentage
	 */
	protected abstract double getPowerData(int index);
	protected abstract double getPeffData(int index);

	public abstract double getPp_CPU_tuil();
	public abstract double getPeakPeff();
	public abstract double getPeakPower();

}
