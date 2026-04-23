/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import org.cloudbus.cloudsim.network.datacenter.NetworkCloudletSpaceSharedScheduler;


/**
 * CloudletScheduler is an abstract class that represents the policy of scheduling performed by a
 * virtual machine to run its {@link Cloudlet Cloudlets}.
 * So, classes extending this must execute Cloudlets. Also, the interface for
 * cloudlet management is also implemented in this class.
 * Each VM has to have its own instance of a CloudletScheduler.
 *
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
//每个VM都有一个CloudletScheduler
public abstract class CloudletScheduler {

	/** The previous time. */
	private double previousTime;

	/** The list of current mips share available for the VM using the scheduler. */
	//目前 Cloudlet 使用的不同vCPU中的可用Mips列表
	private List<Double> currentMipsShare;

	/** The list of cloudlet waiting to be executed on the VM. */
	//等待Vm执行的Cloudlet，SpaceShared的CloudletSchedule策略才会用到
	//因为TimeShared时，所有 Cloudlet 都是同步运行的
	protected List<? extends ResCloudlet> cloudletWaitingList;

	/** The list of cloudlets being executed on the VM. */
	//该VM中正在运行的 Cloudlet 所构成的列表
	protected List<? extends ResCloudlet> cloudletExecList;

	/** The list of paused cloudlets. */
	//该VM中停止运行的 Cloudlet 所构成的列表
	protected List<? extends ResCloudlet> cloudletPausedList;

	/** The list of finished cloudlets. */
	//该VM中已完成的 Cloudlet 所构成的列表，列表中处理过后的 Cloudlet 会remove掉
	protected List<? extends ResCloudlet> cloudletFinishedList;

	/** The list of failed cloudlets. */
	//该VM中运行失败的 Cloudlet 所构成的列表
	protected List<? extends ResCloudlet> cloudletFailedList;

	/**
	 * Creates a new CloudletScheduler object.
         * A CloudletScheduler must be created before starting the actual simulation.
	 *
	 * @pre $none
	 * @post $none
	 */
	public CloudletScheduler() {
		setPreviousTime(0.0);
		cloudletWaitingList = new LinkedList<ResCloudlet>();
		cloudletExecList = new LinkedList<ResCloudlet>();
		cloudletPausedList = new LinkedList<ResCloudlet>();
		cloudletFinishedList = new LinkedList<ResCloudlet>();
		cloudletFailedList = new LinkedList<ResCloudlet>();
	}

	/**
	 * Updates the processing of cloudlets running under management of this scheduler.
	 *
	 * @param currentTime current simulation time
	 * @param mipsShare list with MIPS share of each Pe available to the scheduler
	 * @return the predicted completion time of the earliest finishing cloudlet,
         * or 0 if there is no next events
	 * @pre currentTime >= 0
	 * @post $none
	 */
	//用于维护Vm各vCPU还有多少MIPs可用，以及更新VM中正在运行的cloudlet列表，并对已完成的cloudlet进行处理
	//当然，如果涉及到cloudlet的PAUSE、WAITING、FAIL，也要维护相应的列表
	public abstract double updateVmProcessing(double currentTime, List<Double> mipsShare);

	/**
	 * Receives an cloudlet to be executed in the VM managed by this scheduler.
	 *
	 * @param gl the submited cloudlet (@todo it's a strange param name)
	 * @param fileTransferTime time required to move the required files from the SAN to the VM
	 * @return expected finish time of this cloudlet, or 0 if it is in a waiting queue
	 * @pre gl != null
	 * @post $none
	 */
	//该VM中一个Cloudlet提交到该VM后要处理的事情
	public abstract double cloudletSubmit(Cloudlet gl, double fileTransferTime);

	/**
	 * Receives an cloudlet to be executed in the VM managed by this scheduler.
	 *
	 * @param gl the submited cloudlet
	 * @return expected finish time of this cloudlet, or 0 if it is in a waiting queue
	 * @pre gl != null
	 * @post $none
	 */
	//该VM中一个Cloudlet提交到该VM后要处理的事情
	public abstract double cloudletSubmit(Cloudlet gl);

	/**
	 * Cancels execution of a cloudlet.
	 *
	 * @param clId ID of the cloudlet being canceled
	 * @return the canceled cloudlet, $null if not found
	 * @pre $none
	 * @post $none
	 */
	//该VM中一个Cloudlet接到CANCEL请求后要处理的事情
	public abstract Cloudlet cloudletCancel(int clId);

	/**
	 * Pauses execution of a cloudlet.
	 *
	 * @param clId ID of the cloudlet being paused
	 * @return $true if cloudlet paused, $false otherwise
	 * @pre $none
	 * @post $none
	 */
	//该VM中一个Cloudlet接到PAUSE请求后要处理的事情
	public abstract boolean cloudletPause(int clId);

	/**
	 * Resumes execution of a paused cloudlet.
	 *
	 * @param clId ID of the cloudlet being resumed
	 * @return expected finish time of the cloudlet, 0.0 if queued
	 * @pre $none
	 * @post $none
	 */
	//该VM中一个Cloudlet接到RESUME（继续处理）请求后要处理的事情
	public abstract double cloudletResume(int clId);

	/**
	 * Processes a finished cloudlet.
	 *
	 * @param rcl finished cloudlet
	 * @pre rgl != $null
	 * @post $none
	 */
	//该VM中一个Cloudlet完成后要处理的事情
	public abstract void cloudletFinish(ResCloudlet rcl);

	/**
	 * Gets the status of a cloudlet.
	 *
	 * @param clId ID of the cloudlet
	 * @return status of the cloudlet, -1 if cloudlet not found
	 * @pre $none
	 * @post $none
         *
         * @todo cloudlet status should be an enum
	 */
	public abstract int getCloudletStatus(int clId);

	/**
	 * Informs if there is any cloudlet that finished to execute in the VM managed by this scheduler.
	 *
	 * @return $true if there is at least one finished cloudlet; $false otherwise
	 * @pre $none
	 * @post $none
         * @todo the method name would be isThereFinishedCloudlets to be clearer
	 */
	//用于检查Vm中是否还有已完成的cloudlet
	public abstract boolean isFinishedCloudlets();

	/**
	 * Returns the next cloudlet in the finished list.
	 *
	 * @return a finished cloudlet or $null if the respective list is empty
	 * @pre $none
	 * @post $none
	 */
	//获取一个已完成的cloudlet来处理（因为Datacenter要发送信息给Broker）
	public abstract Cloudlet getNextFinishedCloudlet();

	/**
	 * Returns the number of cloudlets running in the virtual machine.
	 *
	 * @return number of cloudlets running
	 * @pre $none
	 * @post $none
	 */
	public abstract int runningCloudlets();

	/**
	 * Returns one cloudlet to migrate to another vm.
	 *
	 * @return one running cloudlet
	 * @pre $none
	 * @post $none
	 */
	public abstract Cloudlet migrateCloudlet();

	/**
	 * Gets total CPU utilization percentage of all cloudlets, according to CPU UtilizationModel of
	 * each one.
	 *
	 * @param time the time to get the current CPU utilization
	 * @return total utilization
	 */
	//有不完善的地方，具体函数解释参见CloudletSchedulerDynamicWorkload
	public abstract double getTotalUtilizationOfCpu(double time);

	/**
	 * Gets the current requested mips.
	 *
	 * @return the current mips
	 */
	//返回各个vCPU的MIPs被请求数目
	public abstract List<Double> getCurrentRequestedMips();

	/**
	 * Gets the total current available mips for the Cloudlet.
	 *
	 * @param rcl the rcl
	 * @param mipsShare the mips share
	 * @return the total current mips
         * @todo In fact, this method is returning different data depending
         * of the subclass. It is expected that the way the method use to compute
         * the resulting value can be different in every subclass,
         * but is not supposed that each subclass returns a complete different
         * result for the same method of the superclass.
         * In some class such as {@link NetworkCloudletSpaceSharedScheduler},
         * the method returns the average MIPS for the available PEs,
         * in other classes such as {@link CloudletSchedulerDynamicWorkload} it returns
         * the MIPS' sum of all PEs.
	 */
	//这个函数不合理（有待改进），它用于获取VM真实提供给Cloudlet的MIPs数目。
	//反正各个函数scheduler的这个函数，根据不同的cloudlet分配场景都需要大改
	//（比如当Cloudlet有需要多个vCPU，且VM的vCPU数量多于Cloudlet所需的vCPU，这样的场景）
	public abstract double getTotalCurrentAvailableMipsForCloudlet(ResCloudlet rcl, List<Double> mipsShare);

	/**
	 * Gets the total current requested mips for a given cloudlet.
	 *
	 * @param rcl the rcl
	 * @param time the time
	 * @return the total current requested mips for the given cloudlet
	 */
	//这个函数用于获取time时刻rcl总的MIPs请求量（MIPs需要多个vCPU时，要计算各个vCPU的MIPs请求量之和）
	public abstract double getTotalCurrentRequestedMipsForCloudlet(ResCloudlet rcl, double time);

	/**
	 * Gets the total current allocated mips for cloudlet.
	 *
	 * @param rcl the rcl
	 * @param time the time
	 * @return the total current allocated mips for cloudlet
	 */
	//这个函数计算该VM实际分配给Cloudlet的MIPs数量
	public abstract double getTotalCurrentAllocatedMipsForCloudlet(ResCloudlet rcl, double time);

	/**
	 * Gets the current requested ram.
	 *
	 * @return the current requested ram
	 */
	//获取该VM正在运行的cloudlet的RAM请求总量
	public abstract double getCurrentRequestedUtilizationOfRam();

	/**
	 * Gets the current requested bw.
	 *
	 * @return the current requested bw
	 */
	//获取该VM正在运行的cloudlet的Bandwidth请求总量
	public abstract double getCurrentRequestedUtilizationOfBw();

	/**
	 * Gets the previous time.
	 *
	 * @return the previous time
	 */
	//获取上一时间片的时刻
	public double getPreviousTime() {
		return previousTime;
	}

	/**
	 * Sets the previous time.
	 *
	 * @param previousTime the new previous time
	 */
	protected void setPreviousTime(double previousTime) {
		this.previousTime = previousTime;
	}

	/**
	 * Sets the current mips share.
	 *
	 * @param currentMipsShare the new current mips share
	 */
	protected void setCurrentMipsShare(List<Double> currentMipsShare) {
		this.currentMipsShare = currentMipsShare;
	}

	/**
	 * Gets the current mips share.
	 *
	 * @return the current mips share
	 */
	public List<Double> getCurrentMipsShare() {
		return currentMipsShare;
	}

	/**
	 * Gets the cloudlet waiting list.
	 *
	 * @param <T> the generic type
	 * @return the cloudlet waiting list
	 */
	@SuppressWarnings("unchecked")
	public <T extends ResCloudlet> List<T> getCloudletWaitingList() {
		return (List<T>) cloudletWaitingList;
	}

	/**
	 * Cloudlet waiting list.
	 *
	 * @param <T> the generic type
	 * @param cloudletWaitingList the cloudlet waiting list
	 */
	protected <T extends ResCloudlet> void setCloudletWaitingList(List<T> cloudletWaitingList) {
		this.cloudletWaitingList = cloudletWaitingList;
	}

	/**
	 * Gets the cloudlet exec list.
	 *
	 * @param <T> the generic type
	 * @return the cloudlet exec list
	 */
	@SuppressWarnings("unchecked")
	public <T extends ResCloudlet> List<T> getCloudletExecList() {
		return (List<T>) cloudletExecList;
	}

	/**
	 * Sets the cloudlet exec list.
	 *
	 * @param <T> the generic type
	 * @param cloudletExecList the new cloudlet exec list
	 */
	protected <T extends ResCloudlet> void setCloudletExecList(List<T> cloudletExecList) {
		this.cloudletExecList = cloudletExecList;
	}

	/**
	 * Gets the cloudlet paused list.
	 *
	 * @param <T> the generic type
	 * @return the cloudlet paused list
	 */
	@SuppressWarnings("unchecked")
	public <T extends ResCloudlet> List<T> getCloudletPausedList() {
		return (List<T>) cloudletPausedList;
	}

	/**
	 * Sets the cloudlet paused list.
	 *
	 * @param <T> the generic type
	 * @param cloudletPausedList the new cloudlet paused list
	 */
	protected <T extends ResCloudlet> void setCloudletPausedList(List<T> cloudletPausedList) {
		this.cloudletPausedList = cloudletPausedList;
	}

	/**
	 * Gets the cloudlet finished list.
	 *
	 * @param <T> the generic type
	 * @return the cloudlet finished list
	 */
	@SuppressWarnings("unchecked")
	public <T extends ResCloudlet> List<T> getCloudletFinishedList() {
		return (List<T>) cloudletFinishedList;
	}

	/**
	 * Sets the cloudlet finished list.
	 *
	 * @param <T> the generic type
	 * @param cloudletFinishedList the new cloudlet finished list
	 */
	protected <T extends ResCloudlet> void setCloudletFinishedList(List<T> cloudletFinishedList) {
		this.cloudletFinishedList = cloudletFinishedList;
	}

	/**
	 * Gets the cloudlet failed list.
	 *
	 * @param <T> the generic type
	 * @return the cloudlet failed list.
	 */
	@SuppressWarnings("unchecked")
	public <T extends ResCloudlet> List<T>  getCloudletFailedList() {
		return (List<T>) cloudletFailedList;
	}

	/**
	 * Sets the cloudlet failed list.
	 *
	 * @param <T> the generic type
	 * @param cloudletFailedList the new cloudlet failed list.
	 */
	protected <T extends ResCloudlet> void setCloudletFailedList(List<T> cloudletFailedList) {
		this.cloudletFailedList = cloudletFailedList;
	}

}
