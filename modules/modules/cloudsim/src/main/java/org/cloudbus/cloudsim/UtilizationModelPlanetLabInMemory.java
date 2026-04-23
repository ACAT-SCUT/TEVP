package org.cloudbus.cloudsim;

import org.cloudbus.cloudsim.util.MathUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Defines the resource utilization model based on 
 * a <a href="https://www.planet-lab.org">PlanetLab</a>
 * datacenter trace file.
 */
//通过PlanetLab数据集获取Cloudlet的实时CPU利用率（如果是其他数据集的话还需要重新封装）
//PlanetLab数据集只记录了一个Cloudlet各个时间点的CPU利用率（甚至没有分成多个vCPU来记录，
//毕竟如果数据集有具体某时刻cloudlet不同vCPU的利用率的话，Cloudlet能分配给的Vm至少也要有
//这么多个vCPU，这就涉及到Cloudlet层面调度的问题了）
public class UtilizationModelPlanetLabInMemory implements UtilizationModel {
	
	/** The scheduling interval. */
	private double schedulingInterval;

	/** The data (5 min * 288 = 24 hours). */
	private final double[] data; 
	
	/**
	 * Instantiates a new PlanetLab resource utilization model from a trace file.
	 * 
	 * @param inputPath The path of a PlanetLab datacenter trace.
         * @param schedulingInterval
	 * @throws NumberFormatException the number format exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public UtilizationModelPlanetLabInMemory(String inputPath, double schedulingInterval,ArrayList<Double> record)
			throws NumberFormatException,
			IOException {
		//数据集里面每个文件都是288行，所以才定义这个大小，每一行代表一个时间间隔(5min)该Cloudlet占用的CPU利用率
		data = new double[289];//5 min * 288 = 24 hours
		setSchedulingInterval(schedulingInterval);
		BufferedReader input = new BufferedReader(new FileReader(inputPath));
		int n = data.length;
		//读取文件的数据，每个数据占一行。data的数在区间[0,1]，而数据集里面的数据以百分比为单位，所以要除以100
		for (int i = 0; i < n - 1; i++) {
			data[i] = Integer.valueOf(input.readLine()) / 100.0;
			if(data[i]==0.0){// 当VM的CPU利用率为0时，VM会被关闭，因此，在这里将其置为0.1
				data[i]=0.1;
			}
			record.add(data[i]);
		}
		data[n - 1] = data[n - 2];
		input.close();
	}
	
	/**
	 * Instantiates a new PlanetLab resource utilization model with variable data samples
         * from a trace file.
	 * 
	 * @param inputPath The path of a PlanetLab datacenter trace.
	 * @param dataSamples number of samples in the file
	 * @throws NumberFormatException the number format exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public UtilizationModelPlanetLabInMemory(String inputPath, double schedulingInterval, int dataSamples)
			throws NumberFormatException,
			IOException {
		setSchedulingInterval(schedulingInterval);
		data = new double[dataSamples];
		BufferedReader input = new BufferedReader(new FileReader(inputPath));
		int n = data.length;
		for (int i = 0; i < n - 1; i++) {
			data[i] = Integer.valueOf(input.readLine()) / 100.0;
		}
		data[n - 1] = data[n - 2];
		input.close();
	}

	@Override
	public double getUtilization(double time) {
		//这个其实就是将“时间-CPU利用率”构造成分段函数来返回实时的CPU利用率情况
		if (time % getSchedulingInterval() == 0) {
			return data[(int) time / (int) getSchedulingInterval()];
		}
		//当time不为整数个时间间隔时，就要算一下分段函数值才能得到CPU利用率
		int time1 = (int) Math.floor(time / getSchedulingInterval());
		int time2 = (int) Math.ceil(time / getSchedulingInterval());
		double utilization1 = data[time1];
		double utilization2 = data[time2];
		double delta = (utilization2 - utilization1) / ((time2 - time1) * getSchedulingInterval());
		double utilization = utilization1 + delta * (time - time1 * getSchedulingInterval());
		return utilization;
	}

	/**
	 * Sets the scheduling interval.
	 * 
	 * @param schedulingInterval the new scheduling interval
	 */
	public void setSchedulingInterval(double schedulingInterval) {
		this.schedulingInterval = schedulingInterval;
	}

	/**
	 * Gets the scheduling interval.
	 * 
	 * @return the scheduling interval
	 */
	public double getSchedulingInterval() {
		return schedulingInterval;
	}
	
	public double[] getData(){
		return data;
	}
}
