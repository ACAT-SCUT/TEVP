/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;

/**
 * CloudletSchedulerDynamicWorkload implements a policy of scheduling performed by a virtual machine
 * to run its {@link Cloudlet Cloudlets},
 * assuming there is just one cloudlet which is working as an online service.
 * It extends a TimeShared policy, but in fact, considering that there is just
 * one cloudlet for the VM using this scheduler, the cloudlet will not
 * compete for CPU with other ones.
 * Each VM has to have its own instance of a CloudletScheduler.
 *
 * @author Anton Beloglazov
 * @todo The name of the class doesn't represent its goal. A clearer name would be
 * CloudletSchedulerSingleService as its Test Suite
 * @since CloudSim Toolkit 2.0
 */
//每个VM都有一个这样的CloudletScheduler，用于对Cloudlet进行调度
public class CloudletSchedulerDynamicWorkload extends CloudletSchedulerTimeShared {

    /**
     * The individual MIPS capacity of each PE allocated to the VM using the scheduler,
     * considering that all PEs have the same capacity.
     *
     * @todo Despite of the class considers that all PEs have the same capacity,
     * it accepts a list of PEs with different MIPS at the method
     * {@link #updateVmProcessing(double, java.util.List) }
     */
    //该VM单个虚拟内核（PE）的MIPs资源容量
    private double mips;

    /** The number of PEs allocated to the VM using the scheduler. */
    /**
     * 注意，这里的PE是指vCPU，由人为设定的VM类型决定，毕竟 Cloudlet 分给哪种 VM 是不确定的
     */
    private int numberOfPes;

    /**
     * The total MIPS considering all PEs.
     */
    //所有（PE）的MIPs容量
    private double totalMips;

    /**
     * The under allocated MIPS.
     */
    //用来记录各个Cloudlet欠了多少MIPs
    private Map<String, Double> underAllocatedMips;

    /**
     * The cache of the previous time when the {@link #getCurrentRequestedMips()} was called.
     */
    private double cachePreviousTime;

    /**
     * The cache of the last current requested MIPS. @see  #getCurrentRequestedMips()
     */
    private List<Double> cacheCurrentRequestedMips;

    /**
     * Instantiates a new VM scheduler
     *
     * @param mips        The individual MIPS capacity of each PE allocated to the VM using the scheduler,
     *                    considering that all PEs have the same capacity.
     * @param numberOfPes The number of PEs allocated to the VM using the scheduler.
     */
    public CloudletSchedulerDynamicWorkload(double mips, int numberOfPes) {
        super();
        setMips(mips);
        setNumberOfPes(numberOfPes);
                /*@todo There shouldn't be a setter to total mips, considering
                that it is computed from number of PEs and mips.
                If the number of pes of mips is set any time after here,
                the total mips will be wrong. Just the getTotalMips is enough,
                and it have to compute there the total, instead of storing into an attribute.*/
        setTotalMips(getNumberOfPes() * getMips());
        setUnderAllocatedMips(new HashMap<String, Double>());
        setCachePreviousTime(-1);
    }

    @Override
    /**
     * @param currentTime 当前时间
     * @param mipsShare 特定Vm的Mips
     * */
    //用于维护Vm各vCPU还有多少MIPs可用（即Host实际给每个vCPU分配了多少MIPs，
    // 以及更新VM中正在运行的cloudlet列表，并对已完成的cloudlet进行处理
    public double updateVmProcessing(double currentTime, List<Double> mipsShare) {
        setCurrentMipsShare(mipsShare);

        double timeSpan = currentTime - getPreviousTime();
        double nextEvent = Double.MAX_VALUE;
        List<ResCloudlet> cloudletsToFinish = new ArrayList<ResCloudlet>();//记录在这个时间片要完成的Cloudlet

        for (ResCloudlet rcl : getCloudletExecList()) {//当前Vm正在执行的Cloudlet列表
            //CloudletFinishedSoFar是从仿真开始到目前为止这个Cloudlet完成了多少指令，单位：Instruction（所以最后乘了MILLION）
            //实际上也就只有CloudletScheduler相关的类会以 Instruction 为单位，其他地方都是以 MI 为单位
            //这里其实不合理：应该改成 (上底+下底) * 高(timeSpan) / 2
            rcl.updateCloudletFinishedSoFar((long) (timeSpan
                    * getTotalCurrentAllocatedMipsForCloudlet(rcl, getPreviousTime()) * Consts.MILLION));

            if (rcl.getRemainingCloudletLength() == 0) { // finished: remove from the list
                cloudletsToFinish.add(rcl);
                continue;
            } else { // not finish: estimate the finish time
                double estimatedFinishTime = getEstimatedFinishTime(rcl, currentTime);//预估还有多长时间能完成？？
                if (estimatedFinishTime - currentTime < CloudSim.getMinTimeBetweenEvents()) {
                    estimatedFinishTime = currentTime + CloudSim.getMinTimeBetweenEvents();
                }
                if (estimatedFinishTime < nextEvent) {
                    nextEvent = estimatedFinishTime;
                }
            }
        }

        //将要已经完成的Cloudlet从cloudletExeList去掉
        for (ResCloudlet rgl : cloudletsToFinish) {
            getCloudletExecList().remove(rgl);
            cloudletFinish(rgl);
        }

        //更新previousTime
        setPreviousTime(currentTime);

        if (getCloudletExecList().isEmpty()) {
            return 0;
        }

        return nextEvent;
    }

    @Override
    public double cloudletSubmit(Cloudlet cl) {
        return cloudletSubmit(cl, 0);
    }

    @Override
    //一个Cloudlet提交到该VM后要处理的事情
    public double cloudletSubmit(Cloudlet cl, double fileTransferTime) {
        //创建一个Cloudlet状态记录类，并把它设置成正在运行的状态
        ResCloudlet rcl = new ResCloudlet(cl);
        rcl.setCloudletStatus(Cloudlet.INEXEC);

        //一旦Cloudlet分配给了VM以后，numberOfPes就固定了，不会改变
        //你可能不会相信，这machineId和PeId哪都没有被用上，应该是task调度层面才会被用上吧
        for (int i = 0; i < cl.getNumberOfPes(); i++) {
            rcl.setMachineAndPeId(0, i);
        }

        //将该Cloudlet加入到VM正在运行的Cloudlet列表当中
        getCloudletExecList().add(rcl);
        return getEstimatedFinishTime(rcl, getPreviousTime());
    }

    @Override
    //Cloudlet完成后的处理
    public void cloudletFinish(ResCloudlet rcl) {
        rcl.setCloudletStatus(Cloudlet.SUCCESS);
        rcl.finalizeCloudlet();
        getCloudletFinishedList().add(rcl);
    }

    @Override
    //用来获取一个VM正在运行的所有Cloudlet的CPU利用率之和，利用率一般会超100%，这并不合理。
    //因为这玩意其实是用来计算Vm的总MIPs数的，假设每个vCPU的MIPs容量相同，直接乘这个加起来
    //的百分比就可以获得该Vm总的MIPs数请求量（超100%的话就玩蛋了）
    public double getTotalUtilizationOfCpu(double time) {
        double totalUtilization = 0;
        for (ResCloudlet rcl : getCloudletExecList()) {
            totalUtilization += rcl.getCloudlet().getUtilizationOfCpu(time);
        }
        return totalUtilization;
    }

    @Override
    //这里返回的其实是该VM所有vCPU被Cloudlet请求的MIPs数
    //PS：如果扩展成Cloudlet分配给VM的多个vCPU，且所占比例各不相的情形，这个函数要改
    public List<Double> getCurrentRequestedMips() {
        if (getCachePreviousTime() == getPreviousTime()) {
            return getCacheCurrentRequestedMips();
        }
        List<Double> currentMips = new ArrayList<Double>();//记录每个vCPU的CPU利用率
        //目前该VM总共使用了多少MIPS（假设每个vCPU的MIPs数相同），我们假设每个Cloudlet
        //会同时使用多个vCPU，且占用的CPU利用率相等。比如Cloudlet的CPU利用率为10%，
        //则会使用所有vCPU的10%的资源量，这样的话乘totalMips确实是正确的
        double totalMips = getTotalUtilizationOfCpu(getPreviousTime()) * getTotalMips();
        //修改点，把getCurrentRequestedMips全部用Math.floor处理，避免小数出现
        double mipsForPe = Math.floor(totalMips / getNumberOfPes());//平均每个vCPU目前使用了多少MIPS

        //将每个vCPU被请求的MIPs数设定为平均值
        for (int i = 0; i < getNumberOfPes(); i++) {
            currentMips.add(mipsForPe);
        }

        setCachePreviousTime(getPreviousTime());
        setCacheCurrentRequestedMips(currentMips);

        return currentMips;

        /*//这里后续可以扩展成Cloudlet分配给VM的多个vCPU，且所占比例各不相的情形
        if (getCachePreviousTime() == getPreviousTime()) {
            return getCacheCurrentRequestedMips();
        }
		//记录该VM各vCPU被Cloudlet请求的MIPs数，计算途中用来存一下每个vCPU的利用率
        List<Double> currentMips = new ArrayList<Double>(getNumberOfPes());

        //这里需要获取不同Cloudlet的CPU利用率，并获取自身占用各vCPU的利用率，暂存在currentMips里面
        for (ResCloudlet rcl : getCloudletExecList()) {
            Cloudlet cl = rcl.getCloudlet();
            asdf
        }

        setCachePreviousTime(getPreviousTime());
        setCacheCurrentRequestedMips(currentMips);

        return currentMips;*/
    }

    @Override
    //这个函数用于获取time时刻rcl的MIPs请求量，事实上 * getTotalMips()是不合理的
    //只不过是因为作者假设cloudlet同时在多个vCPU以相同的利用率运行时才这样算
    //PS：如果扩展成Cloudlet分配给多vCPU，且具有不同利用率的时候，这个函数要改
    public double getTotalCurrentRequestedMipsForCloudlet(ResCloudlet rcl, double time) {
        return rcl.getCloudlet().getUtilizationOfCpu(time) * getTotalMips();
    }

    @Override
    //这个函数用于获取VM真实提供给Cloudlet的MIPs数目，但是没有判断是否能分配的下就直接累加了（可能是超分用的）
    //PS：如果扩展成Cloudlet分配给多vCPU，且具有不同利用率的时候，这个函数要改
    public double getTotalCurrentAvailableMipsForCloudlet(ResCloudlet rcl, List<Double> mipsShare) {
        double totalCurrentMips = 0.0;
        if (mipsShare != null) {
            int neededPEs = rcl.getNumberOfPes();
            //这里之所以直接这样写是因为CloudSim默认Cloudlet同时使用VM内所有vCPU的资源，且利用率相同
            for (double mips : mipsShare) {
                totalCurrentMips += mips;
                neededPEs--;
                if (neededPEs <= 0) {
                    break;
                }
            }
        }
        return totalCurrentMips;
    }

    @Override
    //这个函数计算该VM实际分配给Cloudlet的MIPs数量
    public double getTotalCurrentAllocatedMipsForCloudlet(ResCloudlet rcl, double time) {
        double totalCurrentRequestedMips = getTotalCurrentRequestedMipsForCloudlet(rcl, time);//time时刻Cloudlet的MIPs请求量
        double totalCurrentAvailableMips = getTotalCurrentAvailableMipsForCloudlet(rcl, getCurrentMipsShare());//time时刻实际的MIPs供应量
        if (totalCurrentRequestedMips > totalCurrentAvailableMips) {
            return totalCurrentAvailableMips;
        }
        return totalCurrentRequestedMips;
    }

    /**
     * Update under allocated mips for cloudlet.
     *
     * @param rcl  the rgl
     * @param mips the mips
     * @todo It is not clear the goal of this method. The related test case
     * doesn't make it clear too. The method doesn't appear to be used anywhere.
     */
    //把新时间片欠缺的mips（函数参数的mips）加到原来记录的该Cloudlet缺少的mips上面，再重新更新一遍Map
    public void updateUnderAllocatedMipsForCloudlet(ResCloudlet rcl, double mips) {
        if (getUnderAllocatedMips().containsKey(rcl.getUid())) {
            mips += getUnderAllocatedMips().get(rcl.getUid());
        }
        getUnderAllocatedMips().put(rcl.getUid(), mips);
    }

    /**
     * Get the estimated completion time of a given cloudlet.
     *
     * @param rcl  the cloudlet
     * @param time the time
     * @return the estimated finish time
     */
    //返回Cloudlet的预计完成时间
    public double getEstimatedFinishTime(ResCloudlet rcl, double time) {
        return time
                + ((rcl.getRemainingCloudletLength()) / getTotalCurrentAllocatedMipsForCloudlet(rcl, time));
    }

    /**
     * Gets the total current mips available for the VM using the scheduler.
     * The total is computed from the {@link #getCurrentMipsShare()}
     *
     * @return the total current mips
     */
    //返回该VM所有vCPU可用MIPs数的和
    public int getTotalCurrentMips() {
        int totalCurrentMips = 0;
        for (double mips : getCurrentMipsShare()) {
            totalCurrentMips += mips;
        }
        return totalCurrentMips;
    }

    /**
     * Sets the total mips.
     *
     * @param mips the new total mips
     */
    public void setTotalMips(double mips) {
        totalMips = mips;
    }

    /**
     * Gets the total mips.
     *
     * @return the total mips
     */
    public double getTotalMips() {
        return totalMips;
    }

    /**
     * Sets the pes number.
     *
     * @param pesNumber the new pes number
     */
    public void setNumberOfPes(int pesNumber) {
        numberOfPes = pesNumber;
    }

    /**
     * Gets the pes number.
     *
     * @return the pes number
     */
    public int getNumberOfPes() {
        return numberOfPes;
    }

    /**
     * Sets the mips.
     *
     * @param mips the new mips
     */
    public void setMips(double mips) {
        this.mips = mips;
    }

    /**
     * Gets the mips.
     *
     * @return the mips
     */
    public double getMips() {
        return mips;
    }

    /**
     * Sets the under allocated mips.
     *
     * @param underAllocatedMips the under allocated mips
     */
    public void setUnderAllocatedMips(Map<String, Double> underAllocatedMips) {
        this.underAllocatedMips = underAllocatedMips;
    }

    /**
     * Gets the under allocated mips.
     *
     * @return the under allocated mips
     */
    public Map<String, Double> getUnderAllocatedMips() {
        return underAllocatedMips;
    }

    /**
     * Gets the cache of previous time.
     *
     * @return the cache previous time
     */
    protected double getCachePreviousTime() {
        return cachePreviousTime;
    }

    /**
     * Sets the cache of previous time.
     *
     * @param cachePreviousTime the new cache previous time
     */
    protected void setCachePreviousTime(double cachePreviousTime) {
        this.cachePreviousTime = cachePreviousTime;
    }

    /**
     * Gets the cache of current requested mips.
     *
     * @return the cache current requested mips
     */
    protected List<Double> getCacheCurrentRequestedMips() {
        return cacheCurrentRequestedMips;
    }

    /**
     * Sets the cache of current requested mips.
     *
     * @param cacheCurrentRequestedMips the new cache current requested mips
     */
    protected void setCacheCurrentRequestedMips(List<Double> cacheCurrentRequestedMips) {
        this.cacheCurrentRequestedMips = cacheCurrentRequestedMips;
    }

}
