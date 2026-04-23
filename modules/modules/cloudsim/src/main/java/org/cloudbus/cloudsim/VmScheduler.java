/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim;

import org.cloudbus.cloudsim.lists.PeList;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VmScheduler is an abstract class that represents the policy used by a Virtual Machine Monitor (VMM)
 * to share processing power of a PM among VMs running in a host.
 * <p>
 * Each host has to use is own instance of a VmScheduler
 * that will so schedule the allocation of host's PEs for VMs running on it.
 *
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
//用于定义Host将资源分配给自身VM的策略（比如TimeShared还是SpaceShared）
public abstract class VmScheduler {

    //Debug专用
    public int Host_Belongs;

    /**
     * The PEs of the host where the scheduler is associated.
     */
    //该Host的物理内核列表
    private List<? extends Pe> peList;

    /**
     * The map of VMs to PEs, where each key is a VM id and each value is
     * a list of PEs allocated to that VM.
     */
    //用于记录VM的各个vCPU分配到了哪些Pe上，每个vCPU对应List<Pe>里面的一个Pe
    // （有可能多个vCPU的Pe都是相同的，vCPU分配给同一个Pe）
    //注意：这里仅仅记录了Vm去了哪个Pe请求资源，实际上请求了多少资源是记录在Pe里面的。
    //每个Pe都有自己的PeProvisioner，PeProvisioner里面会有一个Map作记录
    private Map<String, List<Pe>> peMap;

    /**
     * The map of VMs to MIPS, were each key is a VM id and each value is
     * the currently allocated MIPS from the respective PE to that VM.
     * The PEs where the MIPS capacity is get are defined
     * in the {@link #peMap}.
     *
     * @todo subclasses such as {@link VmSchedulerTimeShared} have an
     * {@link VmSchedulerTimeShared#mipsMapRequested} attribute that
     * may be confused with this one. So, the name of this one
     * may be changed to something such as allocatedMipsMap
     */
    //记录该Host实际应该给不同Vm的各个vCPU分配多少MIPs（List的大小即为某Vm的vCPU数量）
    //这变量的名字起得不好，应该叫做allocatedMipsMap
    private Map<String, List<Double>> mipsMap;

    /**
     * The total available MIPS that can be allocated on demand for VMs.
     */
    //该Host还有多少可用MIPs
    private double availableMips;

    /**
     * The VMs migrating in the host (arriving). It is the list of VM ids
     */
    //该Host上有哪些Vm是刚刚迁入的
    private List<String> vmsMigratingIn;

    /**
     * The VMs migrating out the host (departing). It is the list of VM ids
     */
    //该Host上有哪些Vm是刚刚迁出的
    private List<String> vmsMigratingOut;

    /**
     * Creates a new VmScheduler.
     *
     * @param pelist the list of PEs of the host where the VmScheduler is associated to.
     * @pre peList != $null
     * @post $none
     */
    public VmScheduler(List<? extends Pe> pelist) {
        setPeList(pelist);
        setPeMap(new HashMap<String, List<Pe>>());
        setMipsMap(new HashMap<String, List<Double>>());
        setAvailableMips(PeList.getTotalMips(getPeList()));
        setVmsMigratingIn(new ArrayList<String>());
        setVmsMigratingOut(new ArrayList<String>());
    }

    //修改点：新增：加入了用来Debug的id
    public VmScheduler(List<? extends Pe> pelist, int id) {
        Host_Belongs = id;
        setPeList(pelist);
        setPeMap(new HashMap<String, List<Pe>>());
        setMipsMap(new HashMap<String, List<Double>>());
        setAvailableMips(PeList.getTotalMips(getPeList()));
        setVmsMigratingIn(new ArrayList<String>());
        setVmsMigratingOut(new ArrayList<String>());
    }

    /************************************ VM的CPU分配(具体到哪个PE分配了多少MIPs） ************************************/

    /**
     * Requests the allocation of PEs for a VM.
     *
     * @param vm        the vm
     * @param mipsShare the list of MIPS share to be allocated to a VM
     * @return $true if this policy allows a new VM in the host, $false otherwise
     * @pre $none
     * @post $none
     */
    //将Host的MIPs分配给Vm的不同vCPU
    public abstract boolean allocatePesForVm(Vm vm, List<Double> mipsShare);

    /**
     * Releases PEs allocated to a VM. After that, the PEs may be used
     * on demand by other VMs.
     *
     * @param vm the vm
     * @pre $none
     * @post $none
     */
    public abstract void deallocatePesForVm(Vm vm);

    /**
     * Releases PEs allocated to all the VMs of the host the VmScheduler is associated to.
     * After that, all PEs will be available to be used on demand for requesting VMs.
     *
     * @pre $none
     * @post $none
     */
    //
    public void deallocatePesForAllVms() {
        getMipsMap().clear();
        setAvailableMips(PeList.getTotalMips(getPeList()));
        for (Pe pe : getPeList()) {
            pe.getPeProvisioner().deallocateMipsForAllVms();
        }
    }

    /**
     * Gets the pes allocated for a vm.
     *
     * @param vm the vm
     * @return the pes allocated for the given vm
     */
    //获取该Host分配给VM，各物理内核的MIPs数
    public List<Pe> getPesAllocatedForVM(Vm vm) {
        return getPeMap().get(vm.getUid());
    }

    /**
     * Returns the MIPS share of each host's Pe that is allocated to a given VM.
     *
     * @param vm the vm
     * @return an array containing the amount of MIPS of each pe that is available to the VM
     * @pre $none
     * @post $none
     */
    //这个函数返回Host实际给每个vCPU分配多少MIPs
    public List<Double> getAllocatedMipsForVm(Vm vm) {
        return getMipsMap().get(vm.getUid());
    }

    /**
     * Gets the total allocated MIPS for a VM along all its allocated PEs.
     *
     * @param vm the vm
     * @return the total allocated mips for the vm
     */
    //获取该Host分配给某个Vm的总MIPs数（包括各个vCPU的）
    public double getTotalAllocatedMipsForVm(Vm vm) {
        double allocated = 0;
        List<Double> mipsMap = getAllocatedMipsForVm(vm);
        if (mipsMap != null) {
            for (double mips : mipsMap) {
                allocated += mips;
            }
        }
        return allocated;
    }

    /**
     * Returns maximum available MIPS among all the host's PEs.
     *
     * @return max mips
     */
    //获取物理内核Pe列表里面“可用MIPs数最多的Pe”的MIPs数
    public double getMaxAvailableMips() {
        if (getPeList() == null) {
            Log.printLine("Pe list is empty");
            return 0;
        }

        double max = 0.0;
        for (Pe pe : getPeList()) {
            double tmp = pe.getPeProvisioner().getAvailableMips();
            if (tmp > max) {
                max = tmp;
            }
        }

        return max;
    }

    /**
     * Returns PE capacity in MIPS.
     *
     * @return mips
     * @todo It considers that all PEs have the same capacity,
     * what has been shown doesn't be assured. The peList
     * received by the VmScheduler can be heterogeneous PEs.
     */
    //假设Host中各个物理内核都是MIPs数相同的，直接返回就好了
    public double getPeCapacity() {
        if (getPeList() == null) {
            Log.printLine("Pe list is empty");
            return 0;
        }
        return getPeList().get(0).getMips();
    }

    /**
     * Gets the pe list.
     *
     * @param <T> the generic type
     * @return the pe list
     * @todo The warning have to be checked
     */
    @SuppressWarnings("unchecked")
    //获取物理内核列表
    public <T extends Pe> List<T> getPeList() {
        return (List<T>) peList;
    }

    /**
     * Sets the pe list.
     *
     * @param <T>    the generic type
     * @param peList the pe list
     */
    protected <T extends Pe> void setPeList(List<T> peList) {
        this.peList = peList;
    }

    /**
     * Gets the mips map.
     *
     * @return the mips map
     */
    protected Map<String, List<Double>> getMipsMap() {
        return mipsMap;
    }

    /**
     * Sets the mips map.
     *
     * @param mipsMap the mips map
     */
    protected void setMipsMap(Map<String, List<Double>> mipsMap) {
        this.mipsMap = mipsMap;
    }

    /**
     * Gets the free mips.
     *
     * @return the free mips
     */
    public double getAvailableMips() {
        return availableMips;
    }

    /**
     * Sets the free mips.
     *
     * @param availableMips the new free mips
     */
    protected void setAvailableMips(double availableMips) {
        this.availableMips = availableMips;
    }

    /**
     * Gets the vms migrating out.
     *
     * @return the vms in migration
     */
    public List<String> getVmsMigratingOut() {
        return vmsMigratingOut;
    }

    /**
     * Sets the vms migrating out.
     *
     * @param vmsInMigration the new vms migrating out
     */
    protected void setVmsMigratingOut(List<String> vmsInMigration) {
        vmsMigratingOut = vmsInMigration;
    }

    /**
     * Gets the vms migrating in.
     *
     * @return the vms migrating in
     */
    public List<String> getVmsMigratingIn() {
        return vmsMigratingIn;
    }

    /**
     * Sets the vms migrating in.
     *
     * @param vmsMigratingIn the new vms migrating in
     */
    protected void setVmsMigratingIn(List<String> vmsMigratingIn) {
        this.vmsMigratingIn = vmsMigratingIn;
    }

    /**
     * Gets the pe map.
     *
     * @return the pe map
     */
    public Map<String, List<Pe>> getPeMap() {
        return peMap;
    }

    /**
     * Sets the pe map.
     *
     * @param peMap the pe map
     */
    protected void setPeMap(Map<String, List<Pe>> peMap) {
        this.peMap = peMap;
    }
}
