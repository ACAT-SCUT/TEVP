/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.lists.PeList;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * A host supporting dynamic workloads and performance degradation.
 *
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 2.0
 */
public class HostDynamicWorkload extends Host {

    /**
     * The utilization mips.
     */
    private double utilizationMips;

    /**
     * The previous utilization mips.
     */
    private double previousUtilizationMips;

    /**
     * The host utilization state history.
     */
    //用来记录一个Host的历史数据信息，最后Helper需要用它来统计数据
    private final List<HostStateHistoryEntry> stateHistory = new LinkedList<HostStateHistoryEntry>();

    /**
     * Instantiates a new host.
     *
     * @param id             the id
     * @param ramProvisioner the ram provisioner
     * @param bwProvisioner  the bw provisioner
     * @param storage        the storage capacity
     * @param peList         the host's PEs list
     * @param vmScheduler    the VM scheduler
     */
    public HostDynamicWorkload(
            int id,
            RamProvisioner ramProvisioner,
            BwProvisioner bwProvisioner,
            long storage,
            List<? extends Pe> peList,
            VmScheduler vmScheduler) {
        super(id, ramProvisioner, bwProvisioner, storage, peList, vmScheduler);
        setUtilizationMips(0);
        setPreviousUtilizationMips(0);
    }

    @Override
    //用于更新host里面所有VM的各种Mips信息，真就是摧毁Host上的全部VM，然后重新分配一遍。
    //估计是因为MigrationIn和MigrationOut的维护需要逐个Vm进行触发
    //改进：把RAM也给更新一下（VmScheduler是专门用来分配MIPs的，RAM的话我在这个函数直接更新了）
    //这个函数只在时间片的开头和addMigrationIn函数中进行调用
    //可以在这里统计线性的SLAV
    public double updateVmsProcessing(double currentTime) {

        double smallerTime = super.updateVmsProcessing(currentTime);//这里是给该服务器的Vm逐个调用updateVmProcessing，更新Vm的实时数据
        setPreviousUtilizationMips(getUtilizationMips());
        setUtilizationMips(0);
        double hostTotalRequestedMips = 0;

        //下面，清空所有VM分配关系，按照上个时间片的VM迁移结果，重新分配VM，来统计本时间片的CPU利用率等参数
        //至于为什么它要逐个Vm撤掉，好像是因为VmScheduler有不同的策略（TimeShared和SpaceShared）？
        //getVmScheduler().deallocatePesForAllVm没有对Pe的PeMap进行维护，所以只能
        /*for (Vm vm : getVmList()) {
            getVmScheduler().deallocatePesForVm(vm);//解除Host上所有VM的Pe分配关系
        }*/
        //修改点：同时清空所有Ram和Bw，然后上面这段改成了第1、2句，没必要每次都逐个Vm来撤掉
        getVmScheduler().deallocatePesForAllVms();
        getVmScheduler().getPeMap().clear();
        getRamProvisioner().deallocateRamForAllVms();
        getBwProvisioner().deallocateBwForAllVms();

        //这里会重新放置Vm到Host上，主要是由于Vm的MIPs请求量更新了，所以要重新放置所有Vm来刷新Host的相关变量
        //修改点：这里我把Ram和Bw重新分配一下
        for (Vm vm : getVmList()) {
            getVmScheduler().allocatePesForVm(vm, vm.getCurrentRequestedMips());//重新在Host上给各Vm的vCPU分配内存
            getRamProvisioner().allocateRamForVm(vm, vm.getCurrentRequestedRam());
            getBwProvisioner().allocateBwForVm(vm, vm.getCurrentRequestedBw());
        }

        //System.out.println("\n进入addStateEntry循环\n");

        //下面的代码是用来把Vm的实时状态add到stateEntry列表里面的（分为Vm的状态和Host的状态）
        for (Vm vm : getVmList()) {
            double totalRequestedMips = vm.getCurrentRequestedTotalMips();//统计该Vm请求的Mips
            double totalAllocatedMips = getVmScheduler().getTotalAllocatedMipsForVm(vm);//统计所有已经分配给该Vm的Mips

            if (!Log.isDisabled()) {
                Log.formatLine(
                        "%.2f: [Host #" + getId() + "] Total allocated MIPS for VM #" + vm.getId()
                                + " (Host #" + vm.getHost().getId()
                                + ") is %.2f, was requested %.2f , MIPs_util of Vm is (%.2f%%)",
                        CloudSim.clock(),
                        totalAllocatedMips,
                        totalRequestedMips,
                        //vm.getMips() * vm.getNumberOfPes(),//修改点
                        totalRequestedMips / (vm.getMips() * vm.getNumberOfPes()) * 100);//修改点

                List<Pe> pes = getVmScheduler().getPesAllocatedForVM(vm);
                StringBuilder pesString = new StringBuilder();
                int previousPeId = -1;//修改点
                for (Pe pe : pes) {
                    if (pe.getId() == previousPeId) continue;
                    else {
                        previousPeId = pe.getId();
                        pesString.append(String.format(" PE #" + pe.getId() + ": %.2f.", pe.getPeProvisioner()
                                .getTotalAllocatedMipsForVm(vm)));
                    }
                }
                Log.formatLine(
                        "%.2f: [Host #" + getId() + "] MIPS for VM #" + vm.getId() + " by PEs ("
                                + getNumberOfPes() + " * " + getVmScheduler().getPeCapacity() + ")."
                                + pesString,
                        CloudSim.clock());
            }

            //检查该VM是否刚刚迁入，如果是刚刚迁入的，则在源Host已经记录了该VM的信息，目标Host这边就不重复记录了
            if (getVmsMigratingIn().contains(vm)) {
                Log.formatLine("%.2f: [Host #" + getId() + "] VM #" + vm.getId()
                        + " is being migrated to Host #" + getId(), CloudSim.clock());
            } else {
                //VM迁移MigrationOut()导致性能退化为10%，所以迁移时的不算underAllocated？
                if (totalAllocatedMips + 0.1 < totalRequestedMips) {
                    Log.formatLine("%.2f: [Host #" + getId() + "] Under allocated MIPS for VM #" + vm.getId()
                            + ": %.2f", CloudSim.clock(), totalRequestedMips - totalAllocatedMips);
                }

                //记录VM的状态（Helper里面要用它来统计数据）
                vm.addStateHistoryEntry(
                        currentTime,
                        totalAllocatedMips,
                        totalRequestedMips,
                        (vm.isInMigration() && !getVmsMigratingIn().contains(vm)));

                if (vm.isInMigration()) {//某个VM已经决定要迁移，但还没来得及让Broker执行迁移事件时，会进入这个分支
                    Log.formatLine(
                            "%.2f: [Host #" + getId() + "] VM #" + vm.getId() + " is in migration",
                            CloudSim.clock());
                    //VmSchedulerTimeShared在Allocate时分配的资源*=0.9，而实际上Host在迁移VM这个操作
                    // 上会把剩余的0.1的资源用上，所以这里要恢复一下
                    totalAllocatedMips /= 0.9; // performance degradation due to migration - 10%
                }
            }

            setUtilizationMips(getUtilizationMips() + totalAllocatedMips);
            hostTotalRequestedMips += totalRequestedMips;
        }

        //记录Host的状态
        addStateHistoryEntry(
                currentTime,
                getUtilizationMips(),
                hostTotalRequestedMips,
                (getUtilizationMips() > 0));

        return smallerTime;
    }

    /**
     * Gets the list of completed vms.
     *
     * @return the completed vms
     */
    public List<Vm> getCompletedVms() {
        List<Vm> vmsToRemove = new ArrayList<Vm>();
        for (Vm vm : getVmList()) {
            if (vm.isInMigration()) {
                continue;
            }
            if (vm.getCurrentRequestedTotalMips() == 0) {
                vmsToRemove.add(vm);
            }
        }
        return vmsToRemove;
    }

    /**
     * Gets the max utilization percentage among by all PEs.
     *
     * @return the maximum utilization percentage
     */
    public double getMaxUtilization() {
        return PeList.getMaxUtilization(getPeList());
    }

    /**
     * Gets the max utilization percentage among by all PEs allocated to a VM.
     *
     * @param vm the vm
     * @return the max utilization percentage of the VM
     */
    public double getMaxUtilizationAmongVmsPes(Vm vm) {
        return PeList.getMaxUtilizationAmongVmsPes(getPeList(), vm);
    }

    /**
     * Gets the utilization of memory (in absolute values).
     *
     * @return the utilization of memory
     */
    public double getUtilizationOfRam() {//修改点
        double usedRam = getRamProvisioner().getUsedRam();
        double totalRam = getRamProvisioner().getRam();
        return usedRam / totalRam;
    }

    /**
     * Gets the utilization of bw (in absolute values).
     *
     * @return the utilization of bw
     */
    public double getUtilizationOfBw() {//修改点
        double usedBw = getBwProvisioner().getUsedBw();
        double totalBw = getBwProvisioner().getBw();
        return usedBw / totalBw;
    }

    /**
     * Get current utilization of CPU in percentage.
     *
     * @return current utilization of CPU in percents
     */
    public double getUtilizationOfCpu() {
        double utilization = getUtilizationMips() / getTotalMips();
        if (utilization > 1) {//修改点
            utilization = 1;
        }
        return utilization;
    }

    /**
     * Gets the previous utilization of CPU in percentage.
     *
     * @return the previous utilization of cpu in percents
     */
    public double getPreviousUtilizationOfCpu() {
        double utilization = getPreviousUtilizationMips() / getTotalMips();
        if (utilization > 1) {//修改点
            utilization = 1;
        }
        return utilization;
    }

    /**
     * Get current utilization of CPU in MIPS.
     *
     * @return current utilization of CPU in MIPS
     * @todo This method only calls the  {@link #getUtilizationMips()}.
     * getUtilizationMips may be deprecated and its code copied here.
     */
    public double getUtilizationOfCpuMips() {
        return getUtilizationMips();
    }

    /**
     * Gets the utilization of CPU in MIPS.
     *
     * @return current utilization of CPU in MIPS
     */
    public double getUtilizationMips() {
        return utilizationMips;
    }

    /**
     * Sets the utilization mips.
     *
     * @param utilizationMips the new utilization mips
     */
    protected void setUtilizationMips(double utilizationMips) {
        this.utilizationMips = utilizationMips;
    }

    /**
     * Gets the previous utilization of CPU in mips.
     *
     * @return the previous utilization of CPU in mips
     */
    public double getPreviousUtilizationMips() {
        return previousUtilizationMips;
    }

    /**
     * Sets the previous utilization of CPU in mips.
     *
     * @param previousUtilizationMips the new previous utilization of CPU in mips
     */
    protected void setPreviousUtilizationMips(double previousUtilizationMips) {
        this.previousUtilizationMips = previousUtilizationMips;
    }

    /**
     * Gets the host state history.
     *
     * @return the state history
     */
    public List<HostStateHistoryEntry> getStateHistory() {
        return stateHistory;
    }

    /**
     * Adds a host state history entry.
     *
     * @param time          the time
     * @param allocatedMips the allocated mips
     * @param requestedMips the requested mips
     * @param isActive      the is active
     */
    public void addStateHistoryEntry(double time, double allocatedMips, double requestedMips, boolean isActive) {

        HostStateHistoryEntry newState = new HostStateHistoryEntry(
                time,
                allocatedMips,
                requestedMips,
                isActive);
        if (!getStateHistory().isEmpty()) {
            HostStateHistoryEntry previousState = getStateHistory().get(getStateHistory().size() - 1);
            if (previousState.getTime() == time) {
                getStateHistory().set(getStateHistory().size() - 1, newState);
                return;
            }
        }
        getStateHistory().add(newState);
    }

}
